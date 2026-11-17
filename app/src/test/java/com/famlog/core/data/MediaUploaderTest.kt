package com.famlog.core.data

import com.famlog.core.model.LocalMedia
import com.famlog.core.model.MediaKind
import com.famlog.core.model.SendState
import com.famlog.core.network.ChatApi
import com.famlog.core.network.GroupApi
import com.famlog.core.network.MediaApi
import com.famlog.testing.FakeChatSocket
import com.famlog.testing.FakeFileSource
import com.famlog.testing.FakeMessageDao
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.FakeUploadScheduler
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList

/** 업로드 순서(URL 발급 → PUT → complete → 전송)와 실패 분류 (CHAT-05, 백엔드 S9) */
class MediaUploaderTest {

    private val api = MockWebServer()
    private val storage = MockWebServer()
    private val dao = FakeMessageDao()
    private val socket = FakeChatSocket()
    private val puts = CopyOnWriteArrayList<RecordedRequest>()
    private var putStatus = 200
    private lateinit var chat: ChatRepository
    private lateinit var uploader: MediaUploader
    private var files = FakeFileSource()

    @Before
    fun setUp() {
        api.start()
        storage.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                puts += request
                return MockResponse().setResponseCode(putStatus)
            }
        }
        storage.start()
        val session = FakeSessionStore(userId = 7)
        chat = ChatRepository(
            api.api<ChatApi>(), testApiCaller(), dao, socket, session,
            GroupRepository(api.api<GroupApi>(), testApiCaller(), session), testJson, Clock.systemUTC(),
            FakeUploadScheduler(),
        )
    }

    @After
    fun tearDown() {
        api.shutdown()
        storage.shutdown()
    }

    private fun uploader() = MediaUploader(
        api.api<MediaApi>(), testApiCaller(), dao, files, OkHttpClient(), chat, testJson,
    )

    private suspend fun pendingPhotos(vararg uris: String): String {
        chat.sendMedia(1, uris.map { LocalMedia(it, "image/jpeg", it.length.toLong(), MediaKind.IMAGE) })
        return chat.messages(1).first().first { it.localMedia.first().uri == uris.first() }.key
    }

    private fun urls(vararg ids: Long) = jsonResponse(
        ids.joinToString(prefix = """{"items":[""", postfix = "]}") { id ->
            """{"mediaId":$id,"uploadUrl":"${storage.url("/bucket/$id?X-Amz-Signature=s")}","method":"PUT",
               "headers":{"Content-Type":"image/jpeg"},"expiresAt":"2026-11-17T14:10:00+09:00"}"""
        },
        code = 201,
    )

    private fun completed(id: Long) = jsonResponse("""{"id":$id,"mediaType":"IMAGE","status":"UPLOADED"}""")

    @Test
    fun URL을_받아_헤더_그대로_PUT하고_complete한_뒤_mediaIds로_보낸다() = runTest {
        val key = pendingPhotos("content://a", "content://bb")
        api.enqueue(urls(31, 32))
        api.enqueue(completed(31))
        api.enqueue(completed(32))

        assertEquals(UploadOutcome.DONE, uploader().upload(key))

        val issue = api.takeRequest()
        assertEquals("/api/v1/media/upload-urls", issue.path)
        assertEquals(
            """{"groupId":1,"items":[{"mimeType":"image/jpeg","size":11},{"mimeType":"image/jpeg","size":12}]}""",
            issue.body.readUtf8(),
        )
        assertEquals(listOf("/bucket/31?X-Amz-Signature=s", "/bucket/32?X-Amz-Signature=s"), puts.map { it.path })
        assertEquals("image/jpeg", puts[0].getHeader("Content-Type"))
        assertEquals(null, puts[0].getHeader("Authorization"))
        assertEquals("content://a", puts[0].body.readUtf8())
        assertEquals("/api/v1/media/31/complete", api.takeRequest().path)
        assertEquals("/api/v1/media/32/complete", api.takeRequest().path)

        val (_, request) = socket.sent.single()
        assertEquals("IMAGE", request.type)
        assertEquals(listOf(31L, 32L), request.mediaIds)
    }

    @Test
    fun 저장소_PUT이_실패하면_다시_시도하고_이미_올린_파일은_건너뛴다() = runTest {
        val key = pendingPhotos("content://a", "content://bb")
        api.enqueue(urls(31, 32))
        api.enqueue(completed(31))
        // 두 번째 PUT만 실패시키려고 첫 complete 뒤에 상태를 바꾼다
        storage.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                puts += request
                return MockResponse().setResponseCode(if (puts.size == 1) 200 else 500)
            }
        }

        assertEquals(UploadOutcome.RETRY, uploader().upload(key))
        assertTrue(socket.sent.isEmpty())
        api.takeRequest()
        api.takeRequest()

        // 다음 시도: 남은 한 개만 새 URL을 받는다
        storage.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(200)
        }
        api.enqueue(urls(33))
        api.enqueue(completed(33))
        assertEquals(UploadOutcome.DONE, uploader().upload(key))
        assertEquals("""{"groupId":1,"items":[{"mimeType":"image/jpeg","size":12}]}""", api.takeRequest().body.readUtf8())
        assertEquals(listOf(31L, 33L), socket.sent.single().second.mediaIds)
    }

    @Test
    fun 형식이_거부되면_FAILED() = runTest {
        val key = pendingPhotos("content://a")
        api.enqueue(urls(31))
        api.enqueue(errorResponse(400, "VALIDATION_FAILED"))

        assertEquals(UploadOutcome.FAILED, uploader().upload(key))
        assertEquals(SendState.FAILED.name, dao.get(key)?.sendState)
    }

    @Test
    fun 파일을_읽을_수_없으면_FAILED_서버_5xx면_RETRY() = runTest {
        files = FakeFileSource(unreadable = setOf("content://gone"))
        val gone = pendingPhotos("content://gone")
        api.enqueue(urls(31))
        assertEquals(UploadOutcome.FAILED, uploader().upload(gone))

        val key = pendingPhotos("content://a")
        api.enqueue(errorResponse(503, "INTERNAL_ERROR"))
        assertEquals(UploadOutcome.RETRY, uploader().upload(key))
        assertEquals(SendState.SENDING.name, dao.get(key)?.sendState)
    }
}
