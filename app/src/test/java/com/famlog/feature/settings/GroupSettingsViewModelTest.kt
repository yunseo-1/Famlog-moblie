package com.famlog.feature.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.AppConfigRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.GroupSettingsRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.AiNotice
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.AppFeatures
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupAiSettings
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GroupSettingsViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val me = Member(ME, "윤서", GroupRole.OWNER, null, "딸", null, aiOptOut = false)
    private val mom = Member(MOM, "김엄마", GroupRole.MEMBER, null, "엄마", null, aiOptOut = true)
    private val details = MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.OWNER, listOf(me, mom))))
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { details } returns this@GroupSettingsViewModelTest.details
        coEvery { detail(GROUP) } answers { ApiResult.Success(details.value.getValue(GROUP)) }
    }
    private val notice = AiNotice("1.0", "Anthropic", "미국", listOf("채팅 본문"), listOf("사진"), "등록 후보", "30일", "분석 제외")
    private val settings = mockk<GroupSettingsRepository>(relaxed = true) {
        coEvery { settings(GROUP) } returns ApiResult.Success(GroupAiSettings(enabled = false, noticeVersion = null, noticePending = false))
        coEvery { aiOptOut(GROUP) } returns ApiResult.Success(false)
        coEvery { aiNotice() } returns ApiResult.Success(notice)
    }

    private fun TestScope.started() = GroupSettingsViewModel(
        SavedStateHandle(mapOf("groupId" to GROUP)), settings, groups, FakeSessionStore(userId = ME), appConfig,
    ).also { vm -> backgroundScope.launch { vm.uiState.collect {} } }

    private val features = MutableStateFlow(AppFeatures())
    private val appConfig = mockk<AppConfigRepository> { every { features } returns this@GroupSettingsViewModelTest.features }

    private fun asMember() {
        details.value = mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, listOf(me.copy(aiOptOut = null), mom.copy(aiOptOut = null))))
    }

    @Test
    fun 관리자는_AI_설정과_구성원별_분석_제외를_본다() = runTest(mainRule.dispatcher) {
        coEvery { settings.settings(GROUP) } returns ApiResult.Success(GroupAiSettings(true, "1.0", false))
        coEvery { settings.aiOptOut(GROUP) } returns ApiResult.Success(true)

        val state = started().uiState.value

        assertFalse(state.loading)
        assertTrue(state.isAdmin)
        assertTrue(state.aiEnabled)
        assertTrue(state.aiOptOut)
        assertEquals(listOf(false, true), state.members.map { it.aiOptOut })
    }

    @Test
    fun 구성원에게는_구성원_목록이_없고_AI를_켜고_끌_수_없다() = runTest(mainRule.dispatcher) {
        asMember()
        val viewModel = started()

        assertTrue(viewModel.uiState.value.members.isEmpty())
        viewModel.setAiEnabled(true)

        assertNull(viewModel.uiState.value.notice)
        coVerify(exactly = 0) { settings.aiNotice() }
        coVerify(exactly = 0) { settings.setAiEnabled(any(), any(), any()) }
    }

    @Test
    fun 켜기는_안내를_먼저_보이고_동의하면_보여_준_버전으로_켠다() = runTest(mainRule.dispatcher) {
        coEvery { settings.setAiEnabled(GROUP, true, "1.0") } returns ApiResult.Success(GroupAiSettings(true, "1.0", false))
        val viewModel = started()

        viewModel.setAiEnabled(true)
        assertEquals(AiNoticeSheetState(notice, AiNoticeMode.ENABLE), viewModel.uiState.value.notice)
        coVerify(exactly = 0) { settings.setAiEnabled(any(), any(), any()) }

        viewModel.events.test {
            viewModel.confirmNotice()
            assertEquals(SettingsEvent.Message(UiText.Res(R.string.group_settings_ai_enabled)), awaitItem())
        }
        coVerify { settings.setAiEnabled(GROUP, true, "1.0") }
        assertTrue(viewModel.uiState.value.aiEnabled)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun 그사이_안내가_바뀌었으면_새_안내를_다시_보인다() = runTest(mainRule.dispatcher) {
        val newer = notice.copy(version = "1.1", retention = "14일")
        coEvery { settings.aiNotice() } returns ApiResult.Success(notice) andThen ApiResult.Success(newer)
        coEvery { settings.setAiEnabled(GROUP, true, "1.0") } returns
            ApiResult.Failure(400, ApiError("VALIDATION_FAILED", "입력값을 확인해 주세요.", null, listOf("aiNoticeVersion")))
        val viewModel = started()
        viewModel.setAiEnabled(true)

        viewModel.events.test {
            viewModel.confirmNotice()
            assertEquals(SettingsEvent.Message(UiText.Res(R.string.ai_notice_changed)), awaitItem())
        }

        assertEquals(AiNoticeSheetState(newer, AiNoticeMode.ENABLE), viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.aiEnabled)
    }

    @Test
    fun 끄기는_안내_없이_바로_끈다() = runTest(mainRule.dispatcher) {
        coEvery { settings.settings(GROUP) } returns ApiResult.Success(GroupAiSettings(true, "1.0", false))
        coEvery { settings.setAiEnabled(GROUP, false, null) } returns ApiResult.Success(GroupAiSettings(false, "1.0", false))
        val viewModel = started()

        viewModel.setAiEnabled(false)

        coVerify { settings.setAiEnabled(GROUP, false, null) }
        assertFalse(viewModel.uiState.value.aiEnabled)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun 내_메시지_분석_제외는_구성원도_바꾸고_실패하면_그대로() = runTest(mainRule.dispatcher) {
        asMember()
        coEvery { settings.setAiOptOut(GROUP, true) } returns ApiResult.Success(true) andThen ApiResult.NetworkError
        val viewModel = started()

        viewModel.setAiOptOut(true)
        assertTrue(viewModel.uiState.value.aiOptOut)

        coEvery { settings.setAiOptOut(GROUP, false) } returns ApiResult.NetworkError
        viewModel.setAiOptOut(false)
        assertTrue(viewModel.uiState.value.aiOptOut)
    }

    @Test
    fun 관리자가_내_분석_제외를_바꾸면_구성원별_목록도_다시_받는다() = runTest(mainRule.dispatcher) {
        coEvery { settings.setAiOptOut(GROUP, true) } answers {
            details.value = mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.OWNER, listOf(me.copy(aiOptOut = true), mom)))
            ApiResult.Success(true)
        }
        val viewModel = started()
        coVerify(exactly = 1) { groups.detail(GROUP) }

        viewModel.setAiOptOut(true)

        coVerify(exactly = 2) { groups.detail(GROUP) }
        assertEquals(listOf(true, true), viewModel.uiState.value.members.map { it.aiOptOut })
    }

    @Test
    fun 서버가_AI_분석을_멈췄으면_안내를_보이고_설정은_그대로_바꿀_수_있다() = runTest(mainRule.dispatcher) {
        coEvery { settings.setAiEnabled(GROUP, true, "1.0") } returns ApiResult.Success(GroupAiSettings(true, "1.0", false))
        val viewModel = started()
        assertFalse(viewModel.uiState.value.aiPaused)

        features.value = AppFeatures(aiAnalysis = false)
        assertTrue(viewModel.uiState.value.aiPaused)

        viewModel.setAiEnabled(true)
        viewModel.confirmNotice()
        assertTrue(viewModel.uiState.value.aiEnabled)
    }

    @Test
    fun 안내_다시_보기는_읽기_전용으로_연다() = runTest(mainRule.dispatcher) {
        asMember()
        val viewModel = started()

        viewModel.showNotice()
        assertEquals(AiNoticeMode.VIEW, viewModel.uiState.value.notice?.mode)

        viewModel.confirmNotice()
        coVerify(exactly = 0) { settings.setAiEnabled(any(), any(), any()) }
        viewModel.closeNotice()
        assertNull(viewModel.uiState.value.notice)
    }

    // ---- 그룹 나가기·삭제 (GRP-05) ----

    @Test
    fun 다른_가족이_있는_그룹장은_나가기_전에_넘기라는_안내만() = runTest(mainRule.dispatcher) {
        val viewModel = started()

        viewModel.openLeave()
        assertTrue(viewModel.uiState.value.leave!!.ownerMustTransfer)
        viewModel.confirmLeave()

        coVerify(exactly = 0) { groups.leave(any()) }
    }

    @Test
    fun 혼자인_그룹장은_삭제_경고를_보고_나가면_시작으로_간다() = runTest(mainRule.dispatcher) {
        details.value = mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.OWNER, listOf(me)))
        coEvery { groups.leave(GROUP) } returns ApiResult.Success(Unit)
        val viewModel = started()

        viewModel.openLeave()
        assertTrue(viewModel.uiState.value.leave!!.alone)
        viewModel.events.test {
            viewModel.confirmLeave()
            assertEquals(SettingsEvent.Left, awaitItem())
        }
        assertNull(viewModel.uiState.value.leave)
    }

    @Test
    fun 그사이_가족이_들어와_409면_넘기라는_안내로_바꾼다() = runTest(mainRule.dispatcher) {
        details.value = mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.OWNER, listOf(me)))
        coEvery { groups.leave(GROUP) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.OWNER_TRANSFER_REQUIRED, "그룹장을 넘긴 뒤 나갈 수 있어요.", null))
        val viewModel = started()

        viewModel.openLeave()
        viewModel.confirmLeave()

        assertTrue(viewModel.uiState.value.leave!!.ownerMustTransfer)
    }

    @Test
    fun 구성원은_그룹을_지울_수_없고_그룹장은_이름이_맞아야_지운다() = runTest(mainRule.dispatcher) {
        coEvery { groups.deleteGroup(GROUP) } returns ApiResult.Success(Unit)
        val viewModel = started()

        viewModel.openDelete()
        viewModel.onDeleteNameChange("우리")
        viewModel.confirmDelete()
        coVerify(exactly = 0) { groups.deleteGroup(any()) }

        viewModel.onDeleteNameChange(" 우리 가족 ")
        viewModel.events.test {
            viewModel.confirmDelete()
            assertEquals(SettingsEvent.Left, awaitItem())
        }

        asMember()
        val memberView = started()
        memberView.openDelete()
        assertNull(memberView.uiState.value.delete)
    }

    private companion object {
        const val GROUP = 1L
        const val ME = 1L
        const val MOM = 2L
    }
}
