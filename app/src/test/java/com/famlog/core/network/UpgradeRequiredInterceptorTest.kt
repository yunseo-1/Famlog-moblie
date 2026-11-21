package com.famlog.core.network

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 426 UPGRADE_REQUIRED → 강제 업데이트 화면으로 보낼 이벤트 */
class UpgradeRequiredInterceptorTest {

    private val server = MockWebServer()
    private val events = SessionEvents()
    private val client = OkHttpClient.Builder().addInterceptor(UpgradeRequiredInterceptor(events)).build()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun call() = client.newCall(Request.Builder().url(server.url("/api/v1/groups/me")).build()).execute().close()

    @Test
    fun 응답이_426이면_업데이트_필요를_알리고_다른_응답은_그대로() = runTest {
        events.events.test {
            server.enqueue(MockResponse().setResponseCode(200))
            call()
            expectNoEvents()

            server.enqueue(MockResponse().setResponseCode(426))
            call()
            assertEquals(SessionEvent.UpgradeRequired, awaitItem())
        }
    }
}
