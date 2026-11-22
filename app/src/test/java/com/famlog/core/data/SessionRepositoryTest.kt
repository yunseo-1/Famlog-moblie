package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.network.SessionApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** 로그인 기기 관리 (AUTH-03) */
class SessionRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        server.start()
        repository = SessionRepository(server.api<SessionApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 목록은_지금_기기를_맨_위로_두고_나머지는_서버_순서() = runTest {
        server.enqueue(
            jsonResponse(
                """[
                {"sessionId":7,"deviceName":"Galaxy S25","platform":"ANDROID","createdAt":"2026-11-01T10:00:00+09:00","lastUsedAt":"2026-11-23T09:00:00+09:00","current":false},
                {"sessionId":3,"deviceName":"","platform":"IOS","createdAt":"2026-10-01T10:00:00+09:00","lastUsedAt":null,"current":false},
                {"sessionId":9,"deviceName":"Pixel 9","platform":"ANDROID","createdAt":"2026-11-22T21:00:00+09:00","lastUsedAt":"2026-11-23T08:00:00+09:00","current":true}
                ]""",
            ),
        )

        val sessions = (repository.sessions() as ApiResult.Success).value

        assertEquals("/api/v1/users/me/sessions", server.takeRequest().path)
        assertEquals(listOf(9L, 7L, 3L), sessions.map { it.id })
        assertTrue(sessions.first().current)
        assertEquals(Instant.parse("2026-11-23T00:00:00Z"), sessions[1].lastUsedAt)
        assertNull(sessions[2].deviceName)
        assertNull(sessions[2].lastUsedAt)
    }

    @Test
    fun 다른_기기_로그아웃은_DELETE로_보내고_없는_세션은_404() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(errorResponse(404, "NOT_FOUND", "찾을 수 없어요."))

        assertEquals(ApiResult.Success(Unit), repository.revoke(7))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/users/me/sessions/7", request.path)

        val missing = repository.revoke(8)
        assertTrue(missing is ApiResult.Failure && missing.status == 404)
    }
}
