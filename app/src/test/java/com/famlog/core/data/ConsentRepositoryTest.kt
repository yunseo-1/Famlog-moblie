package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.network.ConsentApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsentRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: ConsentRepository

    @Before
    fun setUp() {
        server.start()
        repository = ConsentRepository(server.api<ConsentApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 다섯_종류_중_위치_동의를_고르고_예전_버전_동의는_유효하지_않다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[
                  {"type":"TERMS","agreed":true,"docVersion":"1.0","currentVersion":"1.0","reconsentRequired":false},
                  {"type":"PRIVACY","agreed":true,"docVersion":"1.0","currentVersion":"1.0","reconsentRequired":false},
                  {"type":"AI_ANALYSIS","agreed":false,"currentVersion":null,"reconsentRequired":false},
                  {"type":"OVERSEAS_TRANSFER","agreed":false,"currentVersion":null,"reconsentRequired":false},
                  {"type":"LOCATION","agreed":true,"docVersion":"0.9","agreedAt":"2026-11-01T10:00:00+09:00",
                   "currentVersion":"1.0","reconsentRequired":true}]}""",
            ),
        )

        val consent = (repository.location() as ApiResult.Success).value

        assertEquals("/api/v1/users/me/consents", server.takeRequest().path)
        assertEquals("1.0", consent.currentVersion)
        assertTrue(consent.agreed)
        assertFalse(consent.valid)
    }

    @Test
    fun 동의는_보여_준_버전을_보내고_철회는_agreed_false만_보낸다() = runTest {
        server.enqueue(jsonResponse("""{"type":"LOCATION","agreed":true,"docVersion":"1.0","currentVersion":"1.0"}"""))
        server.enqueue(jsonResponse("""{"type":"LOCATION","agreed":false,"currentVersion":"1.0"}"""))

        val agreed = (repository.agreeLocation("1.0") as ApiResult.Success).value
        repository.withdrawLocation()

        assertTrue(agreed.valid)
        server.takeRequest().let {
            assertEquals("PUT", it.method)
            assertEquals("/api/v1/users/me/consents/LOCATION", it.path)
            val body = testJson.parseToJsonElement(it.body.readUtf8()).jsonObject
            assertEquals("true", body["agreed"]!!.jsonPrimitive.content)
            assertEquals("1.0", body["docVersion"]!!.jsonPrimitive.content)
        }
        val withdraw = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("false", withdraw["agreed"]!!.jsonPrimitive.content)
        assertFalse(withdraw.containsKey("docVersion"))
    }

    @Test
    fun 약관이_바뀌어_400이면_실패로_돌려준다() = runTest {
        server.enqueue(errorResponse(400, "VALIDATION_FAILED", "약관이 바뀌었어요"))

        val result = repository.agreeLocation("0.9")

        assertEquals(400, (result as ApiResult.Failure).status)
    }
}
