package com.famlog.core.data

import app.cash.turbine.test
import com.famlog.core.datastore.DeviceIdProvider
import com.famlog.core.datastore.FakeTokenStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.AuthTokens
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.SocialProvider
import com.famlog.core.model.errorCode
import com.famlog.core.network.AccountApi
import com.famlog.core.network.SessionEvent
import com.famlog.core.network.SessionEvents
import com.famlog.testing.FakeMessageDao
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private val server = MockWebServer()
    private val tokenStore = FakeTokenStore()
    private val session = FakeSessionStore()
    private val deviceId = mockk<DeviceIdProvider> { coEvery { get() } returns "device-1" }
    private val messageDao = FakeMessageDao()
    private val pushTokens = mockk<PushTokenRepository>(relaxed = true)
    private val sessionEvents = SessionEvents()
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server.start()
        repository = AuthRepository(
            server.api<AccountApi>(), testApiCaller(), tokenStore, session, deviceId, messageDao, pushTokens, sessionEvents,
            "Google Pixel 9",
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 로그인에_성공하면_토큰과_사용자_마지막_그룹을_저장한다() = runTest {
        server.enqueue(jsonResponse(authResponseJson(lastGroupId = "3")))

        val result = repository.login(SocialProvider.KAKAO, "kakao-access")

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/kakao/login", request.path)
        val body = testJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("kakao-access", body["providerToken"]!!.jsonPrimitive.content)
        val device = body["device"]!!.jsonObject
        assertEquals("device-1", device["deviceId"]!!.jsonPrimitive.content)
        assertEquals("ANDROID", device["platform"]!!.jsonPrimitive.content)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals("access-1", tokenStore.tokens?.accessToken)
        assertEquals(42L, session.userId)
        assertEquals(3L, session.currentGroupIdValue)
    }

    @Test
    fun 다른_계정으로_로그인하면_채팅_캐시를_비우고_같은_계정이면_남긴다() = runTest {
        session.saveUser(42, lastGroupId = 3)
        server.enqueue(jsonResponse(authResponseJson(lastGroupId = "3")))
        repository.login(SocialProvider.KAKAO, "kakao-access")
        assertEquals(0, messageDao.clearCount)

        session.saveUser(1, lastGroupId = 3) // 이전 계정
        server.enqueue(jsonResponse(authResponseJson(lastGroupId = "3")))
        repository.login(SocialProvider.KAKAO, "kakao-access")
        assertEquals(1, messageDao.clearCount)
    }

    @Test
    fun 가입_전_계정이면_USER_NOT_REGISTERED를_돌려주고_아무것도_저장하지_않는다() = runTest {
        server.enqueue(errorResponse(404, ErrorCodes.USER_NOT_REGISTERED))

        val result = repository.login(SocialProvider.GOOGLE, "google-id-token")

        assertEquals("/api/v1/auth/google/login", server.takeRequest().path)
        assertEquals(ErrorCodes.USER_NOT_REGISTERED, result.errorCode)
        assertNull(tokenStore.tokens)
        assertNull(session.userId)
    }

    @Test
    fun 가입은_동의_버전과_출생연월을_보내고_마지막_그룹이_없으면_현재_그룹을_비운다() = runTest {
        session.saveUser(1, lastGroupId = 5) // 이전 계정의 값
        server.enqueue(jsonResponse(authResponseJson(lastGroupId = "null"), code = 201))

        val result = repository.signup(SocialProvider.KAKAO, "kakao-access", "윤서", "199003")

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/kakao/signup", request.path)
        val body = testJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("윤서", body["nickname"]!!.jsonPrimitive.content)
        assertEquals("199003", body["birthYm"]!!.jsonPrimitive.content)
        val agreements = body["agreements"]!!.jsonObject
        assertEquals("1.0", agreements["termsVersion"]!!.jsonPrimitive.content)
        assertEquals("1.0", agreements["privacyVersion"]!!.jsonPrimitive.content)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(42L, session.userId)
        assertNull(session.currentGroupIdValue)
    }

    @Test
    fun 만_14세_미만이면_AGE_RESTRICTED() = runTest {
        server.enqueue(errorResponse(403, ErrorCodes.AGE_RESTRICTED, "만 14세 이상만 가입할 수 있어요."))

        val result = repository.signup(SocialProvider.KAKAO, "kakao-access", "윤서", "201501")

        assertEquals(ErrorCodes.AGE_RESTRICTED, result.errorCode)
        assertEquals("만 14세 이상만 가입할 수 있어요.", (result as ApiResult.Failure).error?.message)
        assertNull(tokenStore.tokens)
    }

    // ---- 로그아웃·계정 삭제·복구 (ACC-01) ----

    @Test
    fun 복구할_때만_restore를_보낸다() = runTest {
        server.enqueue(jsonResponse(authResponseJson(lastGroupId = "3")))
        server.enqueue(jsonResponse(authResponseJson(lastGroupId = "3")))

        repository.login(SocialProvider.KAKAO, "kakao-access")
        repository.login(SocialProvider.KAKAO, "kakao-access", restore = true)

        val normal = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val restore = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertNull(normal["restore"])
        assertEquals("true", restore["restore"]!!.jsonPrimitive.content)
    }

    @Test
    fun 로그아웃은_이_기기_세션을_폐기하고_서버가_실패해도_기기에서_정리한다() = runTest {
        tokenStore.tokens = AuthTokens("access-1", "refresh-1")
        session.saveUser(42, lastGroupId = 3)
        server.enqueue(errorResponse(500, "INTERNAL", "잠시 후 다시 시도해 주세요."))

        sessionEvents.events.test {
            repository.logout()
            assertEquals(SessionEvent.LoggedOut, awaitItem())
        }

        val request = server.takeRequest()
        assertEquals("POST /api/v1/auth/logout", "${request.method} ${request.path}")
        assertEquals("""{"refreshToken":"refresh-1"}""", request.body.readUtf8())
        assertNull(tokenStore.tokens)
        assertEquals(1, messageDao.clearCount)
    }

    @Test
    fun 계정_삭제는_사진_지우기를_보내고_성공하면_기기를_정리한다() = runTest {
        tokenStore.tokens = AuthTokens("access-1", "refresh-1")
        server.enqueue(MockResponse().setResponseCode(204))

        sessionEvents.events.test {
            assertEquals(ApiResult.Success(Unit), repository.deleteAccount(deleteMedia = true))
            assertEquals(SessionEvent.LoggedOut, awaitItem())
        }

        val request = server.takeRequest()
        assertEquals("DELETE /api/v1/users/me", "${request.method} ${request.path}")
        assertEquals("""{"deleteMedia":true}""", request.body.readUtf8())
        assertNull(tokenStore.tokens)
    }

    @Test
    fun 그룹장이라_삭제가_막히면_로그인_상태를_그대로_둔다() = runTest {
        tokenStore.tokens = AuthTokens("access-1", "refresh-1")
        server.enqueue(errorResponse(409, ErrorCodes.OWNER_TRANSFER_REQUIRED, "'우리 가족' 그룹의 그룹장을 다른 가족에게 넘긴 뒤 삭제할 수 있어요."))

        val result = repository.deleteAccount(deleteMedia = false)

        assertEquals("""{"deleteMedia":false}""", server.takeRequest().body.readUtf8())
        assertEquals(ErrorCodes.OWNER_TRANSFER_REQUIRED, result.errorCode)
        assertEquals("refresh-1", tokenStore.tokens?.refreshToken)
        assertEquals(0, messageDao.clearCount)
    }

    private fun authResponseJson(lastGroupId: String) = """
        {"tokens":{"accessToken":"access-1","accessTokenExpiresAt":"2026-11-17T01:00:00Z",
                   "refreshToken":"refresh-1","refreshTokenExpiresAt":"2026-12-17T00:00:00Z"},
         "user":{"id":42,"nickname":"윤서","lastGroupId":$lastGroupId}}
    """.trimIndent()
}
