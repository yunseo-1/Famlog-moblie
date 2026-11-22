package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.NotificationSettings
import com.famlog.core.network.GroupSettingsApi
import com.famlog.core.network.NotificationSettingsApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

/** 알림 설정 (SET-02) */
class NotificationSettingsRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: NotificationSettingsRepository

    @Before
    fun setUp() {
        server.start()
        repository = NotificationSettingsRepository(server.api<NotificationSettingsApi>(), server.api<GroupSettingsApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    private val body = """{"previewHidden":false,"quietHoursEnabled":true,"quietStart":"23:30","quietEnd":"06:00"}"""

    @Test
    fun 설정을_읽는다() = runTest {
        server.enqueue(jsonResponse(body))

        val settings = (repository.settings() as ApiResult.Success).value

        assertEquals("/api/v1/users/me/notification-settings", server.takeRequest().path)
        assertEquals(NotificationSettings(false, true, LocalTime.of(23, 30), LocalTime.of(6, 0)), settings)
    }

    @Test
    fun 바꾼_값만_보낸다() = runTest {
        repeat(3) { server.enqueue(jsonResponse(body)) }

        repository.setPreviewHidden(true)
        repository.setQuietHours(false)
        repository.setQuietTimes(LocalTime.of(21, 5), LocalTime.of(7, 0))

        val requests = List(3) { server.takeRequest() }
        assertTrue(requests.all { it.method == "PATCH" && it.path == "/api/v1/users/me/notification-settings" })
        assertEquals(
            listOf("""{"previewHidden":true}""", """{"quietHoursEnabled":false}""", """{"quietStart":"21:05","quietEnd":"07:00"}"""),
            requests.map { it.body.readUtf8() },
        )
    }

    @Test
    fun 시작과_끝이_같으면_서버가_거절한다() = runTest {
        server.enqueue(errorResponse(400, "VALIDATION_FAILED", "시작과 끝 시각이 같을 수 없어요."))

        val result = repository.setQuietTimes(LocalTime.of(7, 0), LocalTime.of(7, 0))

        assertTrue(result is ApiResult.Failure && result.status == 400)
    }

    @Test
    fun 그룹_알림_끄기는_내_구성원_설정으로_보낸다() = runTest {
        server.enqueue(jsonResponse("""{"notifMuted":false,"aiOptOut":true}"""))
        server.enqueue(jsonResponse("""{"notifMuted":true,"aiOptOut":true}"""))

        assertEquals(ApiResult.Success(false), repository.groupMuted(2))
        assertEquals("/api/v1/groups/2/members/me/preferences", server.takeRequest().path)

        assertEquals(ApiResult.Success(true), repository.setGroupMuted(2, true))
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("""{"notifMuted":true}""", request.body.readUtf8())
    }
}
