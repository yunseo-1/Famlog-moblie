package com.famlog.feature.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.ConsentRepository
import com.famlog.core.data.GroupSettingsRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.AiNotice
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Consent
import com.famlog.core.model.GroupAiSettings
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AiNoticePromptViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val notice = AiNotice("1.0", "Anthropic", "미국", listOf("채팅 본문"), listOf("사진"), "등록 후보", "30일", "분석 제외")
    private val settings = mockk<GroupSettingsRepository>(relaxed = true) {
        every { noticeSkipped(GROUP) } returns false
        coEvery { settings(GROUP) } returns ApiResult.Success(GroupAiSettings(enabled = true, noticeVersion = "1.0", noticePending = true))
        coEvery { aiNotice() } returns ApiResult.Success(notice)
    }
    private val consents = mockk<ConsentRepository>(relaxed = true)

    private fun viewModel() = AiNoticePromptViewModel(SavedStateHandle(mapOf("groupId" to GROUP)), settings, consents)

    @Test
    fun 켜진_그룹에서_아직_확인하지_않았으면_안내를_띄운다() = runTest(mainRule.dispatcher) {
        assertEquals(AiNoticeSheetState(notice, AiNoticeMode.CONFIRM), viewModel().prompt.value)
    }

    @Test
    fun 확인했거나_꺼졌거나_이번에_닫았으면_띄우지_않는다() = runTest(mainRule.dispatcher) {
        coEvery { settings.settings(GROUP) } returns ApiResult.Success(GroupAiSettings(enabled = true, noticeVersion = "1.0", noticePending = false))
        assertNull(viewModel().prompt.value)

        every { settings.noticeSkipped(GROUP) } returns true
        assertNull(viewModel().prompt.value)
        coVerify(exactly = 1) { settings.settings(GROUP) }
        coVerify(exactly = 0) { settings.aiNotice() }
    }

    @Test
    fun 확인하면_보여_준_버전으로_기록하고_닫는다() = runTest(mainRule.dispatcher) {
        coEvery { consents.confirmAiNotice("1.0") } returns ApiResult.Success(Consent(Consent.AI_ANALYSIS, true, "1.0", "1.0", false))
        val viewModel = viewModel()

        viewModel.confirm()

        coVerify { consents.confirmAiNotice("1.0") }
        assertNull(viewModel.prompt.value)
    }

    @Test
    fun 그사이_안내가_바뀌었으면_새_안내를_다시_보인다() = runTest(mainRule.dispatcher) {
        val newer = notice.copy(version = "1.1")
        coEvery { settings.aiNotice() } returns ApiResult.Success(notice) andThen ApiResult.Success(newer)
        coEvery { consents.confirmAiNotice("1.0") } returns
            ApiResult.Failure(400, ApiError("VALIDATION_FAILED", "입력값을 확인해 주세요.", null, listOf("docVersion")))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.confirm()
            assertEquals(SettingsEvent.Message(UiText.Res(R.string.ai_notice_changed)), awaitItem())
        }
        assertEquals(AiNoticeSheetState(newer, AiNoticeMode.CONFIRM), viewModel.prompt.value)
    }

    @Test
    fun 분석_제외를_고르면_이_그룹에서_빼고_알린다() = runTest(mainRule.dispatcher) {
        coEvery { settings.setAiOptOut(GROUP, true) } returns ApiResult.Success(true)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.optOut()
            assertEquals(SettingsEvent.Message(UiText.Res(R.string.ai_notice_opted_out)), awaitItem())
        }
        coVerify { settings.setAiOptOut(GROUP, true) }
        coVerify(exactly = 0) { consents.confirmAiNotice(any()) }
        assertNull(viewModel.prompt.value)
    }

    @Test
    fun 버튼_없이_닫으면_앱을_켜_둔_동안_이_그룹은_다시_띄우지_않는다() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.dismiss()

        verify { settings.skipNotice(GROUP) }
        assertNull(viewModel.prompt.value)
    }

    private companion object {
        const val GROUP = 1L
    }
}
