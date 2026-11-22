package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.DataExportStatus
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.errorCode
import com.famlog.core.network.DataExportApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** 내 데이터 내보내기 (ACC-02) */
class DataExportRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: DataExportRepository

    @Before
    fun setUp() {
        server.start()
        repository = DataExportRepository(server.api<DataExportApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    private val requested =
        """{"id":3,"status":"REQUESTED","requestedAt":"2026-11-22T10:00:00+09:00","readyAt":null,"expiresAt":null,"sizeBytes":null,"downloadUrl":null,"fileName":null}"""

    @Test
    fun 요청은_본문_없이_보내고_준비_중_상태를_돌려준다() = runTest {
        server.enqueue(jsonResponse(requested, 202))

        val result = repository.request()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/users/me/data-exports", request.path)
        val export = (result as ApiResult.Success).value
        assertEquals(3L, export.id)
        assertTrue(export.status.preparing)
        assertEquals(Instant.parse("2026-11-22T01:00:00Z"), export.requestedAt)
    }

    @Test
    fun 하루에_한_번을_넘기면_서버_문구와_함께_실패한다() = runTest {
        server.enqueue(errorResponse(429, "RATE_LIMITED", "내보내기는 하루에 한 번 요청할 수 있어요."))

        val result = repository.request()

        assertEquals(ErrorCodes.RATE_LIMITED, result.errorCode)
        assertEquals("내보내기는 하루에 한 번 요청할 수 있어요.", (result as ApiResult.Failure).error?.message)
    }

    @Test
    fun 최근_요청은_목록의_첫_번째이고_없으면_null() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[{"id":5,"status":"READY","requestedAt":"2026-11-22T10:00:00+09:00","readyAt":"2026-11-22T10:01:00+09:00","expiresAt":"2026-11-29T10:01:00+09:00","sizeBytes":2048,"downloadUrl":null,"fileName":null},$requested]}""",
            ),
        )
        server.enqueue(jsonResponse("""{"items":[]}"""))

        val latest = (repository.latest() as ApiResult.Success).value!!
        assertEquals("/api/v1/users/me/data-exports", server.takeRequest().path)
        assertEquals(5L, latest.id)
        assertEquals(DataExportStatus.READY, latest.status)
        assertEquals(Instant.parse("2026-11-29T01:01:00Z"), latest.expiresAt)
        assertEquals(2048L, latest.sizeBytes)
        assertNull(latest.downloadUrl)

        assertNull((repository.latest() as ApiResult.Success).value)
    }

    @Test
    fun 준비된_요청을_조회하면_새_링크와_파일_이름이_온다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"id":5,"status":"READY","requestedAt":"2026-11-22T10:00:00+09:00","readyAt":"2026-11-22T10:01:00+09:00","expiresAt":"2026-11-29T10:01:00+09:00","sizeBytes":2048,"downloadUrl":"http://10.0.2.2:8333/famlog/exports/5.zip?sig=x","fileName":"famlog-export-20261122.zip"}""",
            ),
        )

        val export = (repository.detail(5) as ApiResult.Success).value

        assertEquals("/api/v1/users/me/data-exports/5", server.takeRequest().path)
        assertEquals("http://10.0.2.2:8333/famlog/exports/5.zip?sig=x", export.downloadUrl)
        assertEquals("famlog-export-20261122.zip", export.fileName)
        // 서명이 든 링크는 문자열로 남지 않는다
        assertTrue("sig" !in export.toString())
    }

    @Test
    fun 모르는_상태는_준비_중으로_본다() = runTest {
        server.enqueue(jsonResponse(requested.replace("REQUESTED", "QUEUED")))

        assertEquals(DataExportStatus.REQUESTED, (repository.detail(3) as ApiResult.Success).value.status)
    }
}
