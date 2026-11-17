package com.famlog.core.network

import com.famlog.core.network.stomp.StompFrame
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

enum class SocketState { DISCONNECTED, CONNECTING, CONNECTED }

sealed interface SocketEvent {
    /** CONNECTED를 받았다. 끊긴 동안 놓친 메시지를 채우고 대기열을 보낼 때다 */
    data object Connected : SocketEvent

    /** /sub/groups/{groupId} 그룹 이벤트 */
    data class Group(val groupId: Long, val event: ChatEventEnvelope) : SocketEvent

    /** /user/queue/events 개인 이벤트 (전송 실패, 재전송 확인, 세션 폐기) */
    data class User(val event: ChatEventEnvelope) : SocketEvent

    /** 연결이 끊겼다. 서버가 ERROR 프레임으로 끊었으면 [errorCode]가 있다 (AUTH_TOKEN_EXPIRED 등) */
    data class Closed(val errorCode: String?) : SocketEvent
}

/** 실시간 채팅 연결 (사양서 11.4). 다시 연결할지·언제 할지는 [com.famlog.core.data.ChatSync]가 정한다 */
interface ChatSocket {
    val state: StateFlow<SocketState>
    val events: Flow<SocketEvent>

    fun connect(accessToken: String)
    fun disconnect()

    /** 구독할 그룹. 연결 중이면 바뀐 만큼만 구독·해제하고, 다음 연결 때도 이 목록을 구독한다 */
    fun setGroups(groupIds: Set<Long>)

    /** 연결돼 있지 않으면 false (대기열에 남겨 두고 연결되면 다시 보낸다) */
    fun send(groupId: Long, request: SendMessageRequest): Boolean
    fun sendRead(groupId: Long, lastReadSeq: Long): Boolean
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatSocketUrl

/**
 * OkHttp 웹소켓 위의 STOMP 1.2 클라이언트.
 * - CONNECT 헤더 `Authorization: Bearer {accessToken}`. 토큰은 로그·toString에 남기지 않는다.
 * - 연결 확인은 웹소켓 ping(OkHttp pingInterval)으로 하고, STOMP 하트비트는 쓰지 않는다(`heart-beat:0,0`).
 * - 콜백은 OkHttp 스레드에서 오므로 상태는 [lock]으로 지킨다. 이전 연결의 늦은 콜백은 [Connection] 비교로 버린다.
 */
@Singleton
class StompChatSocket @Inject constructor(
    @ChatSocketClient private val client: OkHttpClient,
    @ChatSocketUrl private val url: String,
    private val json: Json,
) : ChatSocket {

    private val lock = Any()
    private var connection: Connection? = null
    private var groups: Set<Long> = emptySet()

    private val _state = MutableStateFlow(SocketState.DISCONNECTED)
    override val state: StateFlow<SocketState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SocketEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<SocketEvent> = _events.asSharedFlow()

    private inner class Connection(val accessToken: String) : WebSocketListener() {
        lateinit var webSocket: WebSocket
        var connected = false
        val subscribed = mutableSetOf<Long>()
        var closedEmitted = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(
                StompFrame(
                    StompFrame.CONNECT,
                    linkedMapOf(
                        "accept-version" to "1.2",
                        "host" to (response.request.url.host),
                        "heart-beat" to "0,0",
                        "Authorization" to AuthInterceptor.bearer(accessToken),
                    ),
                ).encode(),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = try {
                StompFrame.decode(text)
            } catch (e: IllegalArgumentException) {
                null
            } ?: return
            when (frame.command) {
                StompFrame.CONNECTED -> onConnected(this)
                StompFrame.MESSAGE -> onStompMessage(frame)
                StompFrame.ERROR -> {
                    closed(this, frame.header("message"))
                    webSocket.close(NORMAL_CLOSURE, null)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            closed(this, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closed(this, null)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = closed(this, null)
    }

    override fun connect(accessToken: String) {
        synchronized(lock) {
            if (connection != null) return
            val newConnection = Connection(accessToken)
            connection = newConnection
            _state.value = SocketState.CONNECTING
            newConnection.webSocket = client.newWebSocket(Request.Builder().url(url).build(), newConnection)
        }
    }

    override fun disconnect() {
        val current = synchronized(lock) { connection } ?: return
        if (current.connected) current.webSocket.send(StompFrame(StompFrame.DISCONNECT).encode())
        current.webSocket.close(NORMAL_CLOSURE, null)
        closed(current, null)
    }

    override fun setGroups(groupIds: Set<Long>) {
        synchronized(lock) {
            groups = groupIds
            connection?.takeIf { it.connected }?.let(::syncSubscriptions)
        }
    }

    override fun send(groupId: Long, request: SendMessageRequest): Boolean =
        sendJson("/pub/groups/$groupId/messages", json.encodeToString(SendMessageRequest.serializer(), request))

    override fun sendRead(groupId: Long, lastReadSeq: Long): Boolean =
        sendJson("/pub/groups/$groupId/read", json.encodeToString(ReadRequest.serializer(), ReadRequest(lastReadSeq)))

    private fun sendJson(destination: String, body: String): Boolean {
        val current = synchronized(lock) { connection?.takeIf { it.connected } } ?: return false
        return current.webSocket.send(
            StompFrame(
                StompFrame.SEND,
                mapOf("destination" to destination, "content-type" to "application/json"),
                body,
            ).encode(),
        )
    }

    private fun onConnected(source: Connection) {
        synchronized(lock) {
            if (connection !== source) return
            source.connected = true
            source.webSocket.send(subscribeFrame(USER_SUBSCRIPTION_ID, USER_DESTINATION))
            syncSubscriptions(source)
            _state.value = SocketState.CONNECTED
        }
        _events.tryEmit(SocketEvent.Connected)
    }

    /** [lock] 안에서 부른다 */
    private fun syncSubscriptions(source: Connection) {
        (source.subscribed - groups).forEach { groupId ->
            source.webSocket.send(
                StompFrame(StompFrame.UNSUBSCRIBE, mapOf("id" to groupSubscriptionId(groupId))).encode(),
            )
            source.subscribed.remove(groupId)
        }
        (groups - source.subscribed).forEach { groupId ->
            source.webSocket.send(subscribeFrame(groupSubscriptionId(groupId), "$GROUP_DESTINATION_PREFIX$groupId"))
            source.subscribed.add(groupId)
        }
    }

    private fun onStompMessage(frame: StompFrame) {
        val destination = frame.header("destination") ?: return
        val envelope = try {
            json.decodeFromString(ChatEventEnvelope.serializer(), frame.body)
        } catch (e: SerializationException) {
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        val event = when {
            destination.startsWith(GROUP_DESTINATION_PREFIX) -> {
                val groupId = destination.removePrefix(GROUP_DESTINATION_PREFIX).toLongOrNull() ?: return
                SocketEvent.Group(groupId, envelope)
            }
            // 서버는 /user/queue/events 를 세션별 주소로 바꿔 보내지만 destination 헤더는 구독한 주소다
            destination.endsWith(USER_QUEUE_SUFFIX) -> SocketEvent.User(envelope)
            else -> return
        }
        _events.tryEmit(event)
    }

    private fun closed(source: Connection, errorCode: String?) {
        synchronized(lock) {
            if (source.closedEmitted) return
            source.closedEmitted = true
            if (connection === source) {
                connection = null
                _state.value = SocketState.DISCONNECTED
            }
        }
        _events.tryEmit(SocketEvent.Closed(errorCode))
    }

    private fun subscribeFrame(id: String, destination: String) =
        StompFrame(StompFrame.SUBSCRIBE, mapOf("id" to id, "destination" to destination)).encode()

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val USER_SUBSCRIPTION_ID = "user"
        const val USER_DESTINATION = "/user/queue/events"
        const val USER_QUEUE_SUFFIX = "/queue/events"
        const val GROUP_DESTINATION_PREFIX = "/sub/groups/"

        fun groupSubscriptionId(groupId: Long) = "g-$groupId"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatSocketModule {
    @Binds
    abstract fun bindChatSocket(impl: StompChatSocket): ChatSocket
}
