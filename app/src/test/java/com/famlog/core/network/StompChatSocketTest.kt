package com.famlog.core.network

import com.famlog.core.network.stomp.StompFrame
import com.famlog.testing.testJson
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** MockWebServer의 웹소켓을 STOMP 서버처럼 써서 프레임 주고받기를 확인한다 */
class StompChatSocketTest {

    private val server = MockWebServer()
    private val received = LinkedBlockingQueue<StompFrame>()
    private lateinit var serverSocket: WebSocket
    private lateinit var socket: StompChatSocket

    @Before
    fun setUp() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverSocket = webSocket
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    StompFrame.decode(text)?.let(received::add)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }),
        )
        server.start()
        socket = StompChatSocket(OkHttpClient(), server.url("/ws").toString(), testJson)
    }

    @After
    fun tearDown() {
        socket.disconnect()
        server.shutdown()
    }

    private fun next(): StompFrame = received.poll(5, TimeUnit.SECONDS) ?: error("프레임이 오지 않았다")

    private fun serverSend(frame: StompFrame) = serverSocket.send(frame.encode())

    @Test
    fun CONNECT에_토큰을_넣고_CONNECTED를_받으면_개인_큐와_그룹을_구독한다() = runBlocking {
        socket.setGroups(setOf(1L, 2L))
        socket.connect("access-1")

        val connect = next()
        assertEquals("CONNECT", connect.command)
        assertEquals("Bearer access-1", connect.header("Authorization"))
        assertEquals("1.2", connect.header("accept-version"))

        serverSend(StompFrame("CONNECTED", mapOf("version" to "1.2")))
        val subscriptions = listOf(next(), next(), next()).map { it.header("destination") }
        assertEquals(listOf("/user/queue/events", "/sub/groups/1", "/sub/groups/2"), subscriptions)
        withTimeout(5_000) { socket.state.first { it == SocketState.CONNECTED } }

        // 연결 중에 그룹이 바뀌면 바뀐 만큼만
        socket.setGroups(setOf(2L, 3L))
        val changes = listOf(next(), next()).associate { it.command to (it.header("destination") ?: it.header("id")) }
        assertEquals(mapOf("UNSUBSCRIBE" to "g-1", "SUBSCRIBE" to "/sub/groups/3"), changes)
    }

    @Test
    fun 그룹_이벤트와_개인_이벤트를_나눠_전달한다() = runBlocking {
        val events = mutableListOf<SocketEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { socket.events.collect { events += it } }
        socket.setGroups(setOf(1L))
        socket.connect("access-1")
        next()
        serverSend(StompFrame("CONNECTED"))
        repeat(2) { next() }

        serverSend(
            StompFrame(
                "MESSAGE",
                mapOf("destination" to "/sub/groups/1"),
                """{"type":"message.deleted","data":{"groupId":1,"id":5,"seq":9}}""",
            ),
        )
        serverSend(
            StompFrame(
                "MESSAGE",
                mapOf("destination" to "/user/queue/events"),
                """{"type":"error","data":{"code":"RATE_LIMITED","clientMessageId":"c-1"}}""",
            ),
        )
        withTimeout(5_000) { while (events.size < 3) delay(20) }
        job.cancel()

        assertEquals(SocketEvent.Connected, events[0])
        val group = events[1] as SocketEvent.Group
        assertEquals(1L, group.groupId)
        assertEquals(ChatEventTypes.MESSAGE_DELETED, group.event.type)
        assertEquals(ChatEventTypes.ERROR, (events[2] as SocketEvent.User).event.type)
    }

    @Test
    fun SEND는_연결된_뒤에만_보내고_JSON_본문을_싣는다() = runBlocking {
        assertTrue(!socket.send(1, SendMessageRequest("c-1", body = "안녕")))

        socket.connect("access-1")
        next()
        serverSend(StompFrame("CONNECTED"))
        next() // 개인 큐 구독
        withTimeout(5_000) { socket.state.first { it == SocketState.CONNECTED } }

        assertTrue(socket.send(1, SendMessageRequest("c-1", body = "안녕", replyToId = 3)))
        val send = next()
        assertEquals("/pub/groups/1/messages", send.header("destination"))
        val body = testJson.decodeFromString(SendMessageRequest.serializer(), send.body)
        assertEquals(SendMessageRequest("c-1", "TEXT", "안녕", replyToId = 3), body)
    }

    @Test
    fun ERROR_프레임이면_에러_코드와_함께_끊긴다() = runBlocking {
        val closed = launch(start = CoroutineStart.UNDISPATCHED) {
            val event = socket.events.first { it is SocketEvent.Closed } as SocketEvent.Closed
            assertEquals("AUTH_TOKEN_EXPIRED", event.errorCode)
        }
        socket.connect("expired")
        next()
        serverSend(StompFrame("ERROR", mapOf("message" to "AUTH_TOKEN_EXPIRED")))

        withTimeout(5_000) { closed.join() }
        assertEquals(SocketState.DISCONNECTED, socket.state.value)
    }
}
