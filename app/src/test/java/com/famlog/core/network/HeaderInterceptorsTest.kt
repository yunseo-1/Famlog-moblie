package com.famlog.core.network

import com.famlog.core.datastore.FakeTokenStore
import com.famlog.core.model.AuthTokens
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaderInterceptorsTest {

    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun send(tokenStore: FakeTokenStore) {
        server.enqueue(MockResponse())
        OkHttpClient.Builder()
            .addInterceptor(AppHeadersInterceptor(appVersion = "1.2.3"))
            .addInterceptor(AuthInterceptor(tokenStore))
            .build()
            .newCall(Request.Builder().url(server.url("/x")).build())
            .execute()
            .close()
    }

    @Test
    fun `토큰이 있으면 앱 버전, 플랫폼, Authorization 헤더를 붙인다`() {
        send(FakeTokenStore(AuthTokens("access", "refresh")))

        val request = server.takeRequest()
        assertEquals("1.2.3", request.getHeader("X-App-Version"))
        assertEquals("ANDROID", request.getHeader("X-Platform"))
        assertEquals("Bearer access", request.getHeader("Authorization"))
    }

    @Test
    fun `토큰이 없으면 Authorization 없이 보낸다`() {
        send(FakeTokenStore(null))

        val request = server.takeRequest()
        assertEquals("ANDROID", request.getHeader("X-Platform"))
        assertNull(request.getHeader("Authorization"))
    }
}
