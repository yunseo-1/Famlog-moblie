package com.famlog.feature.group

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.StartDestination
import com.famlog.core.data.StartResolver
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
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

class GroupStartViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>()
    private val resolver = mockk<StartResolver>()
    private val viewModel = GroupStartViewModel(groups, resolver)
    private val detail = GroupDetail(10, "우리 가족", GroupRole.MEMBER, emptyList())

    @Test
    fun 초대_코드는_영문_숫자만_대문자로_8자리까지() {
        viewModel.onCodeChange("k7mp-q2xa")
        assertEquals("K7MPQ2XA", viewModel.state.value.code)
        assertTrue(viewModel.state.value.canJoin)

        viewModel.onCodeChange(" k7 mp q2 xa 99 ")
        assertEquals("K7MPQ2XA", viewModel.state.value.code)

        viewModel.onCodeChange("K7MP")
        assertFalse(viewModel.state.value.canJoin)
    }

    @Test
    fun 참여하면_MEMBER로_들어가_역할_설정으로() = runTest {
        coEvery { groups.join("K7MPQ2XA") } returns ApiResult.Success(detail)
        coEvery { resolver.afterJoin(detail) } returns StartDestination.RoleSetup(10)
        viewModel.onCodeChange("K7MP-Q2XA")

        viewModel.destinations.test {
            viewModel.join()
            assertEquals(StartDestination.RoleSetup(10), awaitItem())
        }
    }

    @Test
    fun 참여_실패_코드별_안내() = runTest {
        viewModel.onCodeChange("K7MPQ2XA")
        val cases = mapOf(
            ErrorCodes.NOT_FOUND to UiText.Res(R.string.join_error_not_found),
            ErrorCodes.INVITE_EXPIRED to UiText.Res(R.string.join_error_expired),
            ErrorCodes.RATE_LIMITED to UiText.Res(R.string.join_error_locked),
            ErrorCodes.GROUP_LIMIT_EXCEEDED to UiText.Plain("이 가족 그룹은 20명까지 함께할 수 있어요."),
        )
        cases.forEach { (code, expected) ->
            coEvery { groups.join(any()) } returns
                ApiResult.Failure(400, ApiError(code, "이 가족 그룹은 20명까지 함께할 수 있어요.", null))
            viewModel.join()
            assertEquals(code, expected, viewModel.state.value.message)
            assertFalse(viewModel.state.value.submitting)
        }

        coEvery { groups.join(any()) } returns ApiResult.NetworkError
        viewModel.join()
        assertEquals(UiText.Res(R.string.error_network), viewModel.state.value.message)
    }

    @Test
    fun 빈_이름으로는_만들지_않고_앞뒤_공백은_뺀다() = runTest {
        viewModel.onNameChange("   ")
        viewModel.create()
        coVerify(exactly = 0) { groups.create(any()) }

        coEvery { groups.create("우리 가족") } returns ApiResult.Success(detail)
        coEvery { resolver.afterJoin(detail) } returns StartDestination.RoleSetup(10)
        viewModel.onNameChange(" 우리 가족 ")
        viewModel.destinations.test {
            viewModel.create()
            assertEquals(StartDestination.RoleSetup(10), awaitItem())
        }
    }
}
