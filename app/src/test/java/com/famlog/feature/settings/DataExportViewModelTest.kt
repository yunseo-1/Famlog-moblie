package com.famlog.feature.settings

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.DataExportRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.DataExport
import com.famlog.core.model.DataExportStatus
import com.famlog.core.model.ErrorCodes
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** 내 데이터 내보내기 화면 (ACC-02) */
class DataExportViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val ready = MutableSharedFlow<Long>()
    private val repository = mockk<DataExportRepository> { every { this@mockk.ready } returns this@DataExportViewModelTest.ready }

    private fun export(status: DataExportStatus, id: Long = 3, url: String? = null) = DataExport(
        id = id,
        status = status,
        requestedAt = Instant.parse("2026-11-22T01:00:00Z"),
        expiresAt = Instant.parse("2026-11-29T01:01:00Z").takeIf { status == DataExportStatus.READY },
        sizeBytes = 2048L.takeIf { status == DataExportStatus.READY },
        downloadUrl = url,
        fileName = "famlog-export-20261122.zip".takeIf { url != null },
    )

    @Test
    fun 요청한_적이_없으면_요청할_수_있다() = runTest(mainRule.dispatcher) {
        coEvery { repository.latest() } returns ApiResult.Success(null)

        val state = DataExportViewModel(repository).state.value

        assertNull(state.export)
        assertFalse(state.loading)
        assertTrue(state.canRequest)
    }

    @Test
    fun 들어오면_최근_요청으로_상태를_되살리고_준비_중이나_준비됨이면_다시_요청할_수_없다() = runTest(mainRule.dispatcher) {
        coEvery { repository.latest() } returnsMany listOf(
            ApiResult.Success(export(DataExportStatus.PROCESSING)),
            ApiResult.Success(export(DataExportStatus.READY)),
            ApiResult.Success(export(DataExportStatus.FAILED)),
            ApiResult.Success(export(DataExportStatus.EXPIRED)),
        )

        assertFalse(DataExportViewModel(repository).state.value.canRequest)
        assertFalse(DataExportViewModel(repository).state.value.canRequest)
        assertTrue(DataExportViewModel(repository).state.value.canRequest)
        assertTrue(DataExportViewModel(repository).state.value.canRequest)
    }

    @Test
    fun 준비_중이면_10초마다_다시_묻고_준비되면_멈춘다() = runTest(mainRule.dispatcher) {
        coEvery { repository.latest() } returns ApiResult.Success(null)
        coEvery { repository.request() } returns ApiResult.Success(export(DataExportStatus.REQUESTED))
        coEvery { repository.detail(3) } returnsMany listOf(
            ApiResult.Success(export(DataExportStatus.PROCESSING)),
            ApiResult.Success(export(DataExportStatus.READY, url = "http://s3/x?sig=1")),
        )
        val viewModel = DataExportViewModel(repository)
        backgroundScope.launch { viewModel.watch() }
        runCurrent()

        viewModel.request()
        runCurrent()
        assertTrue(viewModel.state.value.export!!.status.preparing)
        coVerify(exactly = 0) { repository.detail(any()) }

        advanceTimeBy(DataExportViewModel.POLL_MS + 1)
        assertEquals(DataExportStatus.PROCESSING, viewModel.state.value.export!!.status)
        advanceTimeBy(DataExportViewModel.POLL_MS)
        assertEquals(DataExportStatus.READY, viewModel.state.value.export!!.status)
        // 링크는 화면 상태에 두지 않는다
        assertNull(viewModel.state.value.export!!.downloadUrl)

        advanceTimeBy(DataExportViewModel.POLL_MS * 3)
        coVerify(exactly = 2) { repository.detail(3) }
    }

    @Test
    fun 실시간_준비_알림이_오면_바로_갱신한다() = runTest(mainRule.dispatcher) {
        coEvery { repository.latest() } returns ApiResult.Success(export(DataExportStatus.PROCESSING))
        coEvery { repository.detail(3) } returns ApiResult.Success(export(DataExportStatus.READY))
        val viewModel = DataExportViewModel(repository)

        ready.emit(99)
        coVerify(exactly = 0) { repository.detail(any()) }
        ready.emit(3)

        assertEquals(DataExportStatus.READY, viewModel.state.value.export!!.status)
    }

    @Test
    fun 하루_한_번을_넘기면_서버_문구를_보여주고_상태는_그대로다() = runTest(mainRule.dispatcher) {
        coEvery { repository.latest() } returns ApiResult.Success(export(DataExportStatus.EXPIRED))
        coEvery { repository.request() } returns ApiResult.Failure(
            429,
            ApiError(ErrorCodes.RATE_LIMITED, "내보내기는 하루에 한 번 요청할 수 있어요.", null),
        )
        val viewModel = DataExportViewModel(repository)

        viewModel.events.test {
            viewModel.request()
            assertEquals(DataExportEvent.Message(UiText.Plain("내보내기는 하루에 한 번 요청할 수 있어요.")), awaitItem())
        }
        assertEquals(DataExportStatus.EXPIRED, viewModel.state.value.export!!.status)
        assertFalse(viewModel.state.value.requesting)
    }

    @Test
    fun 내려받기는_누를_때_새_링크를_받아_넘긴다() = runTest(mainRule.dispatcher) {
        coEvery { repository.latest() } returns ApiResult.Success(export(DataExportStatus.READY))
        coEvery { repository.detail(3) } returns ApiResult.Success(export(DataExportStatus.READY, url = "http://s3/x?sig=2"))
        val viewModel = DataExportViewModel(repository)

        viewModel.events.test {
            viewModel.download()
            assertEquals(DataExportEvent.Download("http://s3/x?sig=2", "famlog-export-20261122.zip"), awaitItem())
        }
        assertNull(viewModel.state.value.export!!.downloadUrl)
        assertFalse(viewModel.state.value.downloading)
    }

    @Test
    fun 내려받으려는데_만료됐으면_안내하고_상태를_바꾼다() = runTest(mainRule.dispatcher) {
        coEvery { repository.latest() } returns ApiResult.Success(export(DataExportStatus.READY))
        coEvery { repository.detail(3) } returns ApiResult.Success(export(DataExportStatus.EXPIRED))
        val viewModel = DataExportViewModel(repository)

        viewModel.events.test {
            viewModel.download()
            assertEquals(DataExportEvent.Message(UiText.Res(R.string.export_not_ready)), awaitItem())
        }
        assertEquals(DataExportStatus.EXPIRED, viewModel.state.value.export!!.status)
        assertTrue(viewModel.state.value.canRequest)
    }
}
