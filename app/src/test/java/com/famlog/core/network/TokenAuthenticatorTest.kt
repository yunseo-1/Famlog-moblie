package com.famlog.core.network

import com.famlog.core.datastore.FakeTokenStore
import com.famlog.core.model.AuthTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshCount = AtomicInteger()
    private val sessionEvents = SessionEvents()
    private val loggedOutCount = AtomicInteger()
    private lateinit var tokenStore: FakeTokenStore
    private lateinit var client: OkHttpClient

    /** 서버 동작: /data는 Bearer new만 통과, old는 [dataErrorCode]로 401. /auth/refresh는 [refreshResponse] */
    private var dataErrorCode = "AUTH_TOKEN_EXPIRED"
    private var refreshResponse: () -> MockResponse = {
        tokenResponse("new", "refresh-2").setHeadersDelay(300, TimeUnit.MILLISECONDS)
    }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/auth/refresh" -> {
                    refreshCount.incrementAndGet()
                    refreshResponse()
                }
                "/data" -> when (request.getHeader("Authorization")) {
                    "Bearer new" -> MockResponse().setBody("""{"ok":true}""")
                    else -> error401(dataErrorCode)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        tokenStore = FakeTokenStore(AuthTokens("old", "refresh-1"))

        val parser = ApiErrorParser(json)
        val refreshApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, refreshApi, parser, sessionEvents))
            .build()
        // 로그아웃 이벤트 수를 세는 구독자 (SharedFlow는 구독자가 있어야 이벤트를 받는다)
        Thread {
            runBlocking { sessionEvents.events.collect { loggedOutCount.incrementAndGet() } }
        }.apply { isDaemon = true }.start()
        Thread.sleep(50)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `만료되면 한 번 재발급하고 새 토큰으로 원래 요청을 다시 보낸다`() {
        val response = call()

        assertEquals(200, response)
        assertEquals(1, refreshCount.get())
        assertEquals(AuthTokens("new", "refresh-2"), tokenStore.tokens)
        assertEquals("""{"refreshToken":"refresh-1"}""", server.takeRequestTo("/auth/refresh").body.readUtf8())
    }

    @Test
    fun `동시에 여러 요청이 만료돼도 재발급은 한 번만 보낸다`() {
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val results = (1..4).map {
            pool.submit<Int> {
                start.await()
                call()
            }
        }
        start.countDown()

        assertEquals(listOf(200, 200, 200, 200), results.map { it.get(5, TimeUnit.SECONDS) })
        assertEquals(1, refreshCount.get())
        assertEquals(1, tokenStore.saveCount)
        pool.shutdown()
    }

    @Test
    fun `AUTH_INVALID이면 재발급하지 않고 토큰을 지운 뒤 로그아웃을 알린다`() {
        dataErrorCode = "AUTH_INVALID"

        val response = call()

        assertEquals(401, response)
        assertEquals(0, refreshCount.get())
        assertNull(tokenStore.tokens)
        awaitLoggedOut()
    }

    @Test
    fun `재발급이 거절되면 토큰을 지우고 로그아웃을 알린다`() {
        refreshResponse = { error401("AUTH_INVALID") }

        val response = call()

        assertEquals(401, response)
        assertEquals(1, refreshCount.get())
        assertNull(tokenStore.tokens)
        awaitLoggedOut()
    }

    @Test
    fun `토큰 없이 보낸 요청의 401은 재발급하지 않는다`() {
        tokenStore.tokens = null

        val response = call()

        assertEquals(401, response)
        assertEquals(0, refreshCount.get())
        assertEquals(0, loggedOutCount.get())
    }

    private fun call(): Int =
        client.newCall(Request.Builder().url(server.url("/data")).build()).execute().use { it.code }

    private fun awaitLoggedOut() {
        repeat(50) {
            if (loggedOutCount.get() > 0) return
            Thread.sleep(20)
        }
        assertEquals(1, loggedOutCount.get())
    }

    private fun MockWebServer.takeRequestTo(path: String): RecordedRequest {
        while (true) {
            val request = takeRequest(1, TimeUnit.SECONDS) ?: error("$path 요청이 없습니다")
            if (request.path == path) return request
        }
    }

    private fun error401(code: String) = MockResponse()
        .setResponseCode(401)
        .setBody("""{"code":"$code","message":"m","traceId":"t"}""")

    private fun tokenResponse(access: String, refresh: String) = MockResponse().setBody(
        """{"accessToken":"$access","accessTokenExpiresAt":"2026-11-15T00:30:00Z",""" +
            """"refreshToken":"$refresh","refreshTokenExpiresAt":"2026-11-29T00:00:00Z"}""",
    )
}
