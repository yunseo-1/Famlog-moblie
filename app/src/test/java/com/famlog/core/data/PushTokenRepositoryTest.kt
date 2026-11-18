package com.famlog.core.data

import com.famlog.core.datastore.FakeTokenStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.AuthTokens
import com.famlog.core.network.DeviceApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushTokenRepositoryTest {

    private val server = MockWebServer()
    private val tokenStore = FakeTokenStore(AuthTokens("access", "refresh"))
    private var fcmToken: String? = "fcm-token-1"
    private lateinit var repository: PushTokenRepository

    @Before
    fun setUp() {
        server.start()
        val source = object : PushTokenSource {
            override suspend fun token(): String? = fcmToken
        }
        repository = PushTokenRepository(server.api<DeviceApi>(), testApiCaller(), tokenStore, source, "1.0", TestScope())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 로그인했으면_현재_토큰과_앱_버전을_올린다() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.register()

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/devices/token", request.path)
        val body = testJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("fcm-token-1", body["token"]!!.jsonPrimitive.content)
        assertEquals("1.0", body["appVersion"]!!.jsonPrimitive.content)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun 새_토큰을_받으면_그_토큰을_올린다() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        repository.register("fcm-token-2")

        val body = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("fcm-token-2", body["token"]!!.jsonPrimitive.content)
    }

    @Test
    fun 로그인하지_않았거나_토큰이_없으면_보내지_않는다() = runTest {
        fcmToken = null
        assertNull(repository.register())

        fcmToken = "fcm-token-1"
        tokenStore.clear()
        assertNull(repository.register())

        assertEquals(0, server.requestCount)
    }

    @Test
    fun 서버가_거절하면_실패를_돌려준다() = runTest {
        server.enqueue(errorResponse(401, "AUTH_INVALID"))

        val result = repository.register()

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun 요청_문자열에_토큰이_보이지_않는다() {
        val request = com.famlog.core.network.DeviceTokenRequest("secret-token", "1.0")

        assertTrue("secret-token" !in request.toString())
    }
}
