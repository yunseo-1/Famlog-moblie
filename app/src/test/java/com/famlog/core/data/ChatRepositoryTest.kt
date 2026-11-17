package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.LocalMedia
import com.famlog.core.model.MediaKind
import com.famlog.core.model.MediaStatus
import com.famlog.core.model.MessageType
import com.famlog.core.model.SendState
import com.famlog.core.network.ChatApi
import com.famlog.core.network.ChatEventEnvelope
import com.famlog.core.network.ChatEventTypes
import com.famlog.core.network.GroupApi
import com.famlog.testing.FakeChatSocket
import com.famlog.testing.FakeMessageDao
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.FakeUploadScheduler
import com.famlog.testing.api
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** 보내기·재전송·중복·순서·빈 seq 채우기·읽음 (CHAT-02~04, 18.1 CHAT-02) */
class ChatRepositoryTest {

    private val server = MockWebServer()
    private val dao = FakeMessageDao()
    private val socket = FakeChatSocket()
    private val scheduler = FakeUploadScheduler()
    private val session = FakeSessionStore(userId = ME, currentGroupId = GROUP)
    private val clock = Clock.fixed(Instant.parse("2026-11-17T05:00:00Z"), ZoneId.of("Asia/Seoul"))
    private lateinit var groups: GroupRepository
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        server.start()
        groups = GroupRepository(server.api<GroupApi>(), testApiCaller(), session)
        repository = ChatRepository(
            server.api<ChatApi>(), testApiCaller(), dao, socket, session, groups, testJson, clock, scheduler,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private suspend fun messages(): List<ChatMessage> = repository.messages(GROUP).first()

    @Test
    fun 보내면_먼저_SENDING으로_보이고_서버가_같은_clientMessageId로_돌려주면_그_줄이_SENT가_된다() = runTest {
        repository.send(GROUP, "안녕", replyTo = null)

        val pending = messages().single()
        assertEquals(SendState.SENDING, pending.sendState)
        assertNull(pending.seq)
        val (groupId, request) = socket.sent.single()
        assertEquals(GROUP, groupId)
        assertEquals(pending.clientMessageId, request.clientMessageId)
        assertEquals("안녕", request.body)

        repository.onGroupEvent(GROUP, created(seq = 1, sender = ME, clientId = request.clientMessageId))

        val sent = messages().single()
        assertEquals(SendState.SENT, sent.sendState)
        assertEquals(1L, sent.seq)
    }

    @Test
    fun 연결이_없을_때_보낸_메시지는_대기하다가_연결되면_같은_clientMessageId로_순서대로_다시_보낸다() = runTest {
        socket.connected = false
        repository.send(GROUP, "첫째", replyTo = null)
        repository.send(GROUP, "둘째", replyTo = null)
        assertTrue(socket.sent.isEmpty())
        val ids = messages().map { it.clientMessageId }

        socket.connected = true
        repository.flushPending { }

        assertEquals(listOf("첫째", "둘째"), socket.sent.map { it.second.body })
        assertEquals(ids, socket.sent.map { it.second.clientMessageId })
    }

    @Test
    fun 같은_메시지를_그룹_이벤트와_재전송_확인으로_두_번_받아도_한_줄만_남는다() = runTest {
        repository.send(GROUP, "안녕", replyTo = null)
        val clientId = socket.sent.single().second.clientMessageId

        repository.onGroupEvent(GROUP, created(seq = 1, sender = ME, clientId = clientId))
        repository.onUserEvent(created(seq = 1, sender = ME, clientId = clientId))

        assertEquals(1, dao.all.size)
    }

    @Test
    fun seq에_빈_곳이_보이면_afterSeq로_채우고_seq_순서로_보인다() = runTest {
        server.enqueue(jsonResponse(page(1)))
        repository.refresh(GROUP)
        server.takeRequest()

        // seq 2를 놓치고 3이 먼저 왔다
        server.enqueue(jsonResponse(page(2, 3)))
        repository.onGroupEvent(GROUP, created(seq = 3, sender = OTHER, clientId = "c-3"))

        assertEquals("/api/v1/groups/$GROUP/messages?afterSeq=1&size=50", server.takeRequest().path)
        assertEquals(listOf(1L, 2L, 3L), messages().map { it.seq })
    }

    @Test
    fun 캐시가_없으면_최신_페이지를_있으면_마지막_seq_이후만_받는다() = runTest {
        server.enqueue(jsonResponse(page(4, 5)))
        repository.refresh(GROUP)
        assertEquals("/api/v1/groups/$GROUP/messages?size=50", server.takeRequest().path)

        server.enqueue(jsonResponse(page(6)))
        repository.refresh(GROUP)
        assertEquals("/api/v1/groups/$GROUP/messages?afterSeq=5&size=50", server.takeRequest().path)

        server.enqueue(jsonResponse(page(2, 3, hasMore = true)))
        assertEquals(ApiResult.Success(true), repository.loadOlder(GROUP))
        assertEquals("/api/v1/groups/$GROUP/messages?beforeSeq=4&size=50", server.takeRequest().path)
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), messages().map { it.seq })
    }

    @Test
    fun 캐시가_없는_방의_남이_보낸_메시지는_저장하지_않고_채팅_탭_요약만_바꾼다() = runTest {
        groupsWith(GroupSummary(GROUP, "우리 가족", 3, GroupRole.OWNER))

        repository.onGroupEvent(GROUP, created(seq = 8, sender = OTHER, clientId = "c-8"))

        assertTrue(dao.all.isEmpty())
        val summary = groups.groups.value.single()
        assertEquals(8L, summary.lastMessage?.seq)
        assertEquals(1L, summary.unreadCount)
    }

    @Test
    fun 내가_보낸_메시지는_안_읽은_수에_넣지_않는다() = runTest {
        groupsWith(GroupSummary(GROUP, "우리 가족", 3, GroupRole.OWNER))

        repository.onGroupEvent(GROUP, created(seq = 1, sender = ME, clientId = "c-1"))

        assertEquals(0L, groups.groups.value.single().unreadCount)
    }

    @Test
    fun 읽음_이벤트는_범위_안에서_읽은_사람이_보내지_않은_메시지의_안_읽은_수만_줄인다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[${message(1, sender = ME, unread = 2)},${message(2, sender = OTHER, unread = 1)},
                   ${message(3, sender = ME, unread = 2)}],"hasMore":false}""",
            ),
        )
        repository.refresh(GROUP)

        repository.onGroupEvent(
            GROUP,
            event(ChatEventTypes.READ_UPDATED, """{"groupId":$GROUP,"userId":$OTHER,"fromSeq":1,"lastReadSeq":3}"""),
        )

        assertEquals(listOf(2, 1, 1), messages().map { it.unreadCount })
    }

    @Test
    fun 전송_제한은_SENDING으로_두고_다시_보내라고_알리고_다른_실패는_FAILED() = runTest {
        repository.send(GROUP, "하나", replyTo = null)
        repository.send(GROUP, "둘", replyTo = null)
        val (first, second) = socket.sent.map { it.second.clientMessageId }

        val retry = repository.onUserEvent(
            event(ChatEventTypes.ERROR, """{"code":"RATE_LIMITED","message":"m","clientMessageId":"$first"}"""),
        )
        val noRetry = repository.onUserEvent(
            event(ChatEventTypes.ERROR, """{"code":"VALIDATION_FAILED","message":"m","clientMessageId":"$second"}"""),
        )

        assertTrue(retry)
        assertFalse(noRetry)
        assertEquals(listOf(SendState.SENDING, SendState.FAILED), messages().map { it.sendState })
    }

    @Test
    fun 읽음은_같은_seq를_한_번만_보내고_연결이_없으면_REST로_보낸다() = runTest {
        server.enqueue(jsonResponse(page(1, 2)))
        repository.refresh(GROUP)
        server.takeRequest()

        repository.markRead(GROUP)
        repository.markRead(GROUP)
        assertEquals(listOf(GROUP to 2L), socket.reads)

        socket.connected = false
        server.enqueue(jsonResponse(page(3)))
        repository.refresh(GROUP)
        server.takeRequest()
        server.enqueue(jsonResponse("""{"lastReadSeq":3}"""))
        repository.markRead(GROUP)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/groups/$GROUP/read", request.path)
        assertEquals("""{"lastReadSeq":3}""", request.body.readUtf8())
    }

    @Test
    fun 삭제는_내가_보낸_5분_이내_메시지만() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[${message(1, sender = ME, sentAt = "2026-11-17T13:56:00+09:00")},
                   ${message(2, sender = ME, sentAt = "2026-11-17T13:54:00+09:00")},
                   ${message(3, sender = OTHER, sentAt = "2026-11-17T13:59:00+09:00")}],"hasMore":false}""",
            ),
        )
        repository.refresh(GROUP)

        assertEquals(listOf(true, false, false), messages().map { repository.canDelete(it, ME) })
    }

    @Test
    fun 메시지_JSON의_답장_공감_시스템_종류를_읽는다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[
                   {"id":11,"groupId":$GROUP,"seq":1,"senderId":null,"type":"SYSTEM","body":"윤서님이 들어왔어요.",
                    "payload":{"kind":"MEMBER_JOINED","userId":7},"reactions":[],"unreadCount":0,"deleted":false,
                    "sentAt":"2026-11-17T13:00:00+09:00"},
                   {"id":12,"groupId":$GROUP,"seq":2,"senderId":$OTHER,"clientMessageId":"c-2","type":"TEXT","body":"응",
                    "replyTo":{"id":10,"seq":0,"senderId":$ME,"type":"TEXT","body":null,"deleted":false},
                    "reactions":[{"emoji":"HEART","userIds":[$ME]},{"emoji":"FUTURE","userIds":[1]}],
                    "unreadCount":0,"deleted":false,"sentAt":"2026-11-17T13:01:00+09:00"},
                   {"id":13,"groupId":$GROUP,"seq":3,"senderId":$OTHER,"type":"POLL","body":null,"reactions":[],
                    "unreadCount":0,"deleted":false,"sentAt":"2026-11-17T13:02:00+09:00"}
                ],"hasMore":false}""",
            ),
        )
        repository.refresh(GROUP)

        val (system, reply, poll) = messages()
        assertTrue(system.isSystem)
        assertEquals("윤서님이 들어왔어요.", system.body)
        assertNull(reply.replyTo?.body) // 내가 들어오기 전 메시지
        assertEquals(listOf("HEART"), reply.reactions.map { it.emoji.name }) // 모르는 공감은 버린다
        assertEquals(MessageType.POLL, poll.type)
    }

    @Test
    fun 사진은_한_묶음_동영상은_하나씩_메시지를_만들고_업로드를_맡긴다() = runTest {
        repository.sendMedia(
            GROUP,
            listOf(photo("p1"), video("v1"), photo("p2"), video("v2")),
        )

        val list = messages()
        assertEquals(listOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.VIDEO), list.map { it.type })
        assertEquals(listOf("p1", "p2"), list[0].localMedia.map { it.uri })
        assertEquals(listOf("v1"), list[1].localMedia.map { it.uri })
        assertEquals(list.map { it.key }, scheduler.scheduled)
        assertTrue(socket.sent.isEmpty()) // 다 올리기 전에는 보내지 않는다
    }

    @Test
    fun 대기열은_올리는_중인_사진_메시지를_건너뛰고_다_올린_것은_mediaIds와_함께_보낸다() = runTest {
        repository.sendMedia(GROUP, listOf(photo("p1"), photo("p2")))
        repository.send(GROUP, "글", replyTo = null)
        socket.sent.clear()
        val key = messages().first { it.isMedia }.key

        repository.flushPending { }
        assertEquals(listOf("TEXT"), socket.sent.map { it.second.type })

        // 업로드가 끝났다
        dao.updateLocalMedia(
            key,
            testJson.encodeToString(
                ListSerializer(LocalMediaJson.serializer()),
                listOf(photo("p1").copy(mediaId = 31).toJson(), photo("p2").copy(mediaId = 32).toJson()),
            ),
        )
        repository.sendIfReady(key)

        val (_, request) = socket.sent.last()
        assertEquals("IMAGE", request.type)
        assertEquals(listOf(31L, 32L), request.mediaIds)
        assertNull(request.body)
    }

    @Test
    fun 덜_올린_실패_메시지를_다시_보내면_업로드부터_다시_하고_지우면_작업도_취소한다() = runTest {
        repository.sendMedia(GROUP, listOf(video("v1")))
        val key = messages().single().key
        dao.updateSendState(key, SendState.FAILED.name)
        scheduler.scheduled.clear()

        repository.retry(key)
        assertEquals(listOf(key), scheduler.scheduled)
        assertEquals(SendState.SENDING, messages().single().sendState)

        repository.discard(key)
        assertEquals(listOf(key), scheduler.cancelled)
        assertTrue(messages().isEmpty())
    }

    @Test
    fun 처리_끝_이벤트가_오면_그_사진만_READY로_바뀐다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[{"id":101,"groupId":$GROUP,"seq":1,"senderId":$OTHER,"clientMessageId":"c-1","type":"IMAGE",
                   "media":[{"id":31,"mediaType":"IMAGE","status":"UPLOADED"},{"id":32,"mediaType":"IMAGE","status":"UPLOADED"}],
                   "reactions":[],"unreadCount":0,"deleted":false,"sentAt":"2026-11-17T13:00:00+09:00"}],"hasMore":false}""",
            ),
        )
        repository.refresh(GROUP)

        repository.onGroupEvent(
            GROUP,
            event(
                ChatEventTypes.MEDIA_UPDATED,
                """{"groupId":$GROUP,"messageId":101,"media":{"id":32,"mediaType":"IMAGE","status":"READY","width":1440,"height":1080}}""",
            ),
        )

        val media = messages().single().media
        assertEquals(listOf(MediaStatus.UPLOADED, MediaStatus.READY), media.map { it.status })
        assertEquals(1440, media[1].width)
    }

    private fun photo(uri: String) = LocalMedia(uri, "image/jpeg", 100, MediaKind.IMAGE)

    private fun video(uri: String) = LocalMedia(uri, "video/mp4", 1000, MediaKind.VIDEO, durationSec = 12)

    private suspend fun groupsWith(vararg summaries: GroupSummary) {
        server.enqueue(
            jsonResponse(
                summaries.joinToString(prefix = "[", postfix = "]") {
                    """{"id":${it.id},"name":"${it.name}","memberCount":${it.memberCount},"myRole":"${it.myRole}","unreadCount":0}"""
                },
            ),
        )
        groups.refreshGroups()
        server.takeRequest()
    }

    private fun event(type: String, data: String) =
        ChatEventEnvelope(type, testJson.parseToJsonElement(data))

    private fun created(seq: Long, sender: Long?, clientId: String?): ChatEventEnvelope =
        ChatEventEnvelope(
            ChatEventTypes.MESSAGE_CREATED,
            testJson.parseToJsonElement(message(seq, sender, clientId = clientId)),
        )

    private fun page(vararg seqs: Long, hasMore: Boolean = false) =
        """{"items":[${seqs.joinToString(",") { message(it, sender = OTHER) }}],"hasMore":$hasMore}"""

    private fun message(
        seq: Long,
        sender: Long?,
        clientId: String? = "c-$seq",
        unread: Int = 0,
        sentAt: String = "2026-11-17T13:00:00+09:00",
    ) = """{"id":${100 + seq},"groupId":$GROUP,"seq":$seq,"senderId":$sender,
        "clientMessageId":${clientId?.let { "\"$it\"" } ?: "null"},"type":"TEXT","body":"본문 $seq",
        "reactions":[],"unreadCount":$unread,"deleted":false,"sentAt":"$sentAt"}"""

    private companion object {
        const val GROUP = 1L
        const val ME = 7L
        const val OTHER = 8L
    }
}
