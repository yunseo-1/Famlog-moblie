package com.famlog.feature.group

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.ReportTarget
import com.famlog.core.model.ReportTargetType
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MemberViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val owner = Member(ME, "윤서", GroupRole.OWNER, null, "딸", null)
    private val admin = Member(MOM, "김엄마", GroupRole.ADMIN, null, "엄마", null)
    private val member = Member(DAD, "김아빠", GroupRole.MEMBER, null, "아빠", null)
    private val groups = mockk<GroupRepository>()

    private fun viewModel() = MemberViewModel(SavedStateHandle(mapOf("groupId" to GROUP)), groups)

    @Test
    fun 역할별로_할_수_있는_것만_보인다() {
        assertEquals(
            listOf(MemberAction.MAKE_MEMBER, MemberAction.TRANSFER_OWNER, MemberAction.REMOVE, MemberAction.REPORT),
            memberActions(ME, GroupRole.OWNER, admin),
        )
        assertEquals(
            listOf(MemberAction.MAKE_ADMIN, MemberAction.TRANSFER_OWNER, MemberAction.REMOVE, MemberAction.REPORT),
            memberActions(ME, GroupRole.OWNER, member),
        )
        // 관리자는 그룹장을 건드릴 수 없고 위임도 못 한다
        assertEquals(listOf(MemberAction.REPORT), memberActions(MOM, GroupRole.ADMIN, owner))
        assertEquals(listOf(MemberAction.MAKE_ADMIN, MemberAction.REMOVE, MemberAction.REPORT), memberActions(MOM, GroupRole.ADMIN, member))
        assertEquals(listOf(MemberAction.REPORT), memberActions(DAD, GroupRole.MEMBER, admin))
        assertEquals(emptyList<MemberAction>(), memberActions(ME, GroupRole.OWNER, owner))
    }

    @Test
    fun 나_자신은_시트를_열지_않는다() {
        val viewModel = viewModel()
        viewModel.open(owner, ME, GroupRole.OWNER)
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun 권한_바꾸기는_바로_보내고_알린다() = runTest(mainRule.dispatcher) {
        coEvery { groups.changeRole(GROUP, DAD, GroupRole.ADMIN) } returns ApiResult.Success(Unit)
        val viewModel = viewModel()
        viewModel.open(member, ME, GroupRole.OWNER)

        viewModel.events.test {
            viewModel.perform(MemberAction.MAKE_ADMIN)
            assertEquals(MemberEvent.Message(UiText.Res(R.string.member_done_admin, listOf("아빠"))), awaitItem())
        }
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun 그룹장_넘기기와_내보내기는_확인한_뒤에만_보낸다() = runTest(mainRule.dispatcher) {
        coEvery { groups.changeRole(GROUP, MOM, GroupRole.OWNER) } returns ApiResult.Success(Unit)
        coEvery { groups.removeMember(GROUP, MOM) } returns ApiResult.Success(Unit)
        val viewModel = viewModel()
        viewModel.open(admin, ME, GroupRole.OWNER)

        viewModel.perform(MemberAction.TRANSFER_OWNER)
        assertEquals(MemberAction.TRANSFER_OWNER, viewModel.sheet.value?.confirm)
        coVerify(exactly = 0) { groups.changeRole(any(), any(), any()) }
        viewModel.cancelConfirm()
        assertNull(viewModel.sheet.value?.confirm)

        viewModel.perform(MemberAction.TRANSFER_OWNER)
        viewModel.confirm()
        coVerify { groups.changeRole(GROUP, MOM, GroupRole.OWNER) }

        viewModel.open(admin, ME, GroupRole.OWNER)
        viewModel.perform(MemberAction.REMOVE)
        coVerify(exactly = 0) { groups.removeMember(any(), any()) }
        viewModel.confirm()
        coVerify { groups.removeMember(GROUP, MOM) }
    }

    @Test
    fun 서버가_거절하면_문구를_보이고_시트는_남는다() = runTest(mainRule.dispatcher) {
        coEvery { groups.changeRole(GROUP, MOM, GroupRole.OWNER) } returns
            ApiResult.Failure(400, ApiError("VALIDATION_FAILED", "삭제 예정인 계정에는 그룹장을 넘길 수 없어요.", null))
        val viewModel = viewModel()
        viewModel.open(admin, ME, GroupRole.OWNER)
        viewModel.perform(MemberAction.TRANSFER_OWNER)

        viewModel.events.test {
            viewModel.confirm()
            assertEquals(MemberEvent.Message(UiText.Plain("삭제 예정인 계정에는 그룹장을 넘길 수 없어요.")), awaitItem())
        }
        assertEquals(admin, viewModel.sheet.value?.member)
        assertNull(viewModel.sheet.value?.confirm)
    }

    @Test
    fun 신고하기는_구성원_신고_시트로_넘긴다() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.open(admin, DAD, GroupRole.MEMBER)

        viewModel.events.test {
            viewModel.perform(MemberAction.REPORT)
            assertEquals(MemberEvent.Report(ReportTarget(ReportTargetType.USER, MOM, GROUP)), awaitItem())
        }
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun 할_수_없는_동작은_무시한다() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.open(owner, MOM, GroupRole.ADMIN)

        viewModel.perform(MemberAction.REMOVE)

        assertNull(viewModel.sheet.value?.confirm)
        coVerify(exactly = 0) { groups.removeMember(any(), any()) }
    }

    private companion object {
        const val GROUP = 1L
        const val ME = 1L
        const val MOM = 2L
        const val DAD = 3L
    }
}
