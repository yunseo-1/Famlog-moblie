package com.famlog.feature.group

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.core.data.GroupRepository
import com.famlog.core.model.ApiResult
import com.famlog.core.model.FamilyRole
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoleSetupViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>()
    private val me = Member(7, "윤서", GroupRole.MEMBER, null, null, "#14a3b8")
    private val detail = GroupDetail(10, "우리 가족", GroupRole.MEMBER, listOf(me))

    private fun viewModel(): RoleSetupViewModel {
        coEvery { groups.detail(10) } returns ApiResult.Success(detail)
        coEvery { groups.me(detail) } returns me
        return RoleSetupViewModel(SavedStateHandle(mapOf("groupId" to 10L)), groups)
    }

    @Test
    fun 그룹_이름과_서버가_고른_색을_기본값으로_보여주고_역할을_골라야_저장할_수_있다() {
        val viewModel = viewModel()

        val state = viewModel.state.value
        assertEquals("우리 가족", state.groupName)
        assertEquals("#14A3B8", state.calendarColor)
        assertFalse(state.canSave)

        viewModel.selectRole(FamilyRole.DAUGHTER)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun 저장하면_역할_호칭_색을_보내고_끝낸다() = runTest {
        val viewModel = viewModel()
        coEvery { groups.updateMyMember(any(), any(), any(), any()) } returns ApiResult.Success(me)
        viewModel.selectRole(FamilyRole.SON)
        viewModel.onTitleChange("  큰아들 ")
        viewModel.selectColor("#e8a33c")

        viewModel.done.test {
            viewModel.save()
            awaitItem()
        }
        coVerify { groups.updateMyMember(10, FamilyRole.SON, "큰아들", "#E8A33C") }
    }

    @Test
    fun 빈_호칭은_보내지_않는다() = runTest {
        val viewModel = viewModel()
        coEvery { groups.updateMyMember(any(), any(), any(), any()) } returns ApiResult.Success(me)
        viewModel.selectRole(FamilyRole.OTHER)
        viewModel.onTitleChange("   ")

        viewModel.save()

        coVerify { groups.updateMyMember(10, FamilyRole.OTHER, null, "#14A3B8") }
    }

    @Test
    fun 그룹을_못_읽으면_다시_시도할_수_있다() {
        coEvery { groups.detail(10) } returns ApiResult.NetworkError
        val viewModel = RoleSetupViewModel(SavedStateHandle(mapOf("groupId" to 10L)), groups)
        assertEquals("", viewModel.state.value.groupName)
        assertFalse(viewModel.state.value.loading)

        coEvery { groups.detail(10) } returns ApiResult.Success(detail)
        coEvery { groups.me(detail) } returns me
        viewModel.load()
        assertEquals("우리 가족", viewModel.state.value.groupName)
    }
}
