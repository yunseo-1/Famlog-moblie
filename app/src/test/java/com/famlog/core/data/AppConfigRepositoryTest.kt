package com.famlog.core.data

import com.famlog.core.datastore.AppPrefs
import com.famlog.core.model.ApiResult
import com.famlog.core.model.AppFeatures
import com.famlog.core.network.AppConfigApi
import com.famlog.testing.api
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 앱 설정 (SCR-00): 강제·선택 업데이트, 공지 한 번, 기능 스위치 */
class AppConfigRepositoryTest {

    private val server = MockWebServer()
    private var seen: String? = null
    private val prefs = mockk<AppPrefs>(relaxed = true) {
        coEvery { seenNoticeUpdatedAt() } answers { seen }
        coEvery { setSeenNoticeUpdatedAt(any()) } answers { seen = firstArg() }
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun repository(version: String = "1.0") = AppConfigRepository(server.api<AppConfigApi>(), testApiCaller(), prefs, version)

    private fun config(min: String = "0.0.0", latest: String? = null, notice: String? = null, noticeAt: String? = null, nearby: Boolean = true) =
        jsonResponse(
            """{"platform":"ANDROID","minSupportedVersion":"$min","latestVersion":${latest?.let { "\"$it\"" }},
               "notice":${notice?.let { "\"$it\"" }},"noticeUpdatedAt":${noticeAt?.let { "\"$it\"" }},
               "features":{"nearby":$nearby,"photoLocation":false,"aiAnalysis":true}}""",
        )

    @Test
    fun 최소_지원_버전보다_낮을_때만_강제_업데이트() = runTest {
        server.enqueue(config(min = "1.0.1"))
        val repository = repository()
        assertFalse(repository.updateRequired())

        repository.refresh()

        assertEquals("/api/v1/app/config", server.takeRequest().path)
        assertTrue(repository.updateRequired())

        server.enqueue(config(min = "1.0.0"))
        repository.refresh()
        assertFalse(repository.updateRequired())
    }

    @Test
    fun 새_공지는_한_번만_보이고_닫으면_선택_업데이트를_한_번_묻는다() = runTest {
        server.enqueue(config(latest = "1.1.0", notice = "11/25 새벽 점검", noticeAt = "2026-11-21T10:00:00+09:00"))
        val repository = repository()
        repository.refresh()

        val notice = repository.nextPrompt()
        assertEquals(StartPrompt.Notice("11/25 새벽 점검", "2026-11-21T10:00:00+09:00"), notice)
        repository.markNoticeSeen("2026-11-21T10:00:00+09:00")
        coVerify { prefs.setSeenNoticeUpdatedAt("2026-11-21T10:00:00+09:00") }

        assertEquals(StartPrompt.OptionalUpdate, repository.nextPrompt())
        assertNull(repository.nextPrompt())
    }

    @Test
    fun 공지가_바뀌면_다시_보이고_최신_버전이면_업데이트를_묻지_않는다() = runTest {
        seen = "2026-11-20T09:00:00+09:00"
        server.enqueue(config(latest = "1.0.0", notice = "새 공지", noticeAt = "2026-11-21T10:00:00+09:00"))
        val repository = repository()
        repository.refresh()

        assertEquals(StartPrompt.Notice("새 공지", "2026-11-21T10:00:00+09:00"), repository.nextPrompt())
        repository.markNoticeSeen("2026-11-21T10:00:00+09:00")
        assertNull(repository.nextPrompt())
    }

    @Test
    fun 기능_스위치는_받기_전엔_기본값이고_받으면_서버_값() = runTest {
        val repository = repository()
        assertEquals(AppFeatures(), repository.features.first())
        assertNull(repository.nextPrompt())

        server.enqueue(config(nearby = false))
        assertTrue(repository.refresh() is ApiResult.Success)

        assertFalse(repository.features.first().nearby)
    }
}
