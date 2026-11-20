package com.famlog.feature.report

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.ReportRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ReportReason
import com.famlog.core.model.ReportTarget
import com.famlog.core.model.ReportTargetType
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val reports = mockk<ReportRepository>()
    private val message = ReportTarget(ReportTargetType.MESSAGE, 30)
    private val ai = ReportTarget(ReportTargetType.AI_OUTPUT, 9)

    @Test
    fun AI_제안에만_AI가_틀렸어요_사유가_있다() {
        assertFalse(ReportReason.AI_INACCURATE in message.reasons)
        assertTrue(ReportReason.AI_INACCURATE in ai.reasons)

        val viewModel = ReportViewModel(reports)
        viewModel.open(message)
        viewModel.onReasonChange(ReportReason.AI_INACCURATE)
        assertNull(viewModel.sheet.value?.reason)
    }

    @Test
    fun 사유를_고르지_않았거나_기타인데_내용이_없으면_보내지_않고_안내한다() = runTest(mainRule.dispatcher) {
        val viewModel = ReportViewModel(reports)
        viewModel.open(message)

        viewModel.submit()
        assertTrue(viewModel.sheet.value!!.showErrors)

        viewModel.onReasonChange(ReportReason.OTHER)
        viewModel.onDetailChange("  ")
        assertFalse(viewModel.sheet.value!!.canSubmit)
        viewModel.submit()

        coVerify(exactly = 0) { reports.report(any(), any(), any()) }
        assertTrue(viewModel.sheet.value!!.detailMissing)
    }

    @Test
    fun 접수되면_시트를_닫고_접수_안내를_보인다() = runTest(mainRule.dispatcher) {
        coEvery { reports.report(ai, ReportReason.AI_INACCURATE, "날짜가 달라요") } returns ApiResult.Success(5)
        val viewModel = ReportViewModel(reports)
        viewModel.open(ai)
        viewModel.onReasonChange(ReportReason.AI_INACCURATE)
        viewModel.onDetailChange("날짜가 달라요")

        viewModel.events.test {
            viewModel.submit()
            assertEquals(UiText.Res(R.string.report_done), awaitItem())
        }
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun 실패하면_시트를_남기고_서버_문구를_보인다() = runTest(mainRule.dispatcher) {
        coEvery { reports.report(message, ReportReason.SPAM, "") } returns
            ApiResult.Failure(404, ApiError("NOT_FOUND", "찾을 수 없어요.", null))
        val viewModel = ReportViewModel(reports)
        viewModel.open(message)
        viewModel.onReasonChange(ReportReason.SPAM)

        viewModel.events.test {
            viewModel.submit()
            assertEquals(UiText.Plain("찾을 수 없어요."), awaitItem())
        }
        assertFalse(viewModel.sheet.value!!.saving)
        assertEquals(ReportReason.SPAM, viewModel.sheet.value!!.reason)
    }

    @Test
    fun 내용은_500자까지만_받는다() {
        val viewModel = ReportViewModel(reports)
        viewModel.open(message)

        viewModel.onDetailChange("가".repeat(600))

        assertEquals(ReportViewModel.MAX_DETAIL, viewModel.sheet.value!!.detail.length)
    }
}
