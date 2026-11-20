package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.ReportReason
import com.famlog.core.model.ReportTarget
import com.famlog.core.model.ReportTargetType
import com.famlog.core.network.ReportApi
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

class ReportRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: ReportRepository

    @Before
    fun setUp() {
        server.start()
        repository = ReportRepository(server.api<ReportApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun created(id: Long, type: String, targetId: Long, reason: String) = jsonResponse(
        """{"id":$id,"targetType":"$type","targetId":$targetId,"reason":"$reason","status":"OPEN","createdAt":"2026-11-20T12:00:00+09:00"}""",
        201,
    )

    @Test
    fun 메시지_신고는_대상과_사유만_보내고_빈_내용은_빼고_접수_ID를_돌려준다() = runTest {
        server.enqueue(created(5, "MESSAGE", 30, "SPAM"))

        val result = repository.report(ReportTarget(ReportTargetType.MESSAGE, 30), ReportReason.SPAM, "   ")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/reports", request.path)
        assertEquals("""{"targetType":"MESSAGE","targetId":30,"reason":"SPAM"}""", request.body.readUtf8())
        assertEquals(ApiResult.Success(5L), result)
    }

    @Test
    fun 구성원_신고는_그룹을_함께_보내고_내용은_다듬어_보낸다() = runTest {
        server.enqueue(created(6, "USER", 2, "OTHER"))

        repository.report(ReportTarget(ReportTargetType.USER, 2, groupId = 1), ReportReason.OTHER, "  계속 같은 사진을 보내요 ")

        assertEquals(
            """{"targetType":"USER","targetId":2,"groupId":1,"reason":"OTHER","detail":"계속 같은 사진을 보내요"}""",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun 볼_수_없는_대상이면_서버_오류를_돌려준다() = runTest {
        server.enqueue(errorResponse(404, "NOT_FOUND", "찾을 수 없어요."))

        val result = repository.report(ReportTarget(ReportTargetType.AI_OUTPUT, 9), ReportReason.AI_INACCURATE, null)

        assertTrue(result is ApiResult.Failure && result.status == 404)
    }
}
