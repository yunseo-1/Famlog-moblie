package com.famlog.navigation

import com.famlog.core.datastore.AppPrefs
import app.cash.turbine.test
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.StartDestination
import com.famlog.core.data.StartResolver
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.Invite
import com.famlog.core.network.SessionEvents
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 그룹 전환 시트 (GRP-03, SCR-70) */
class MainViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val groupList = MutableStateFlow(
        listOf(GroupSummary(1, "우리 가족", 4, GroupRole.OWNER), GroupSummary(2, "외가", 6, GroupRole.MEMBER)),
    )
    private val currentId = MutableStateFlow<Long?>(1)
    private val groups = mockk<GroupRepository> {
        every { groups } returns groupList
        every { currentGroupId } returns currentId
    }
    private val resolver = mockk<StartResolver>()
    private val appPrefs = mockk<AppPrefs>(relaxed = true)
    private val viewModel = MainViewModel(SessionEvents(), groups, resolver, PendingChatOpen(), appPrefs)

    @Test
    fun 현재_그룹을_다시_고르면_서버에_보내지_않고_시트만_닫는다() = runTest {
        viewModel.events.test {
            viewModel.switchTo(1)
            assertEquals(MainEvent.CloseSwitcher, awaitItem())
        }
        coVerify(exactly = 0) { groups.switchTo(any()) }
    }

    @Test
    fun 역할을_정한_그룹으로_전환하면_시트만_닫는다() = runTest {
        coEvery { groups.switchTo(2) } coAnswers {
            currentId.value = 2
            ApiResult.Success(Unit)
        }
        coEvery { resolver.enter(2) } returns StartDestination.Main

        viewModel.events.test {
            viewModel.switchTo(2)
            assertEquals(MainEvent.CloseSwitcher, awaitItem())
            expectNoEvents()
        }
        val state = viewModel.switcherState.first { it.groups.isNotEmpty() }
        assertEquals("외가", state.currentGroup?.name)
    }

    @Test
    fun 역할이_없는_그룹으로_전환하면_역할_설정으로() = runTest {
        coEvery { groups.switchTo(2) } returns ApiResult.Success(Unit)
        coEvery { resolver.enter(2) } returns StartDestination.RoleSetup(2)

        viewModel.events.test {
            viewModel.switchTo(2)
            assertEquals(MainEvent.CloseSwitcher, awaitItem())
            assertEquals(MainEvent.Navigate(StartDestination.RoleSetup(2)), awaitItem())
        }
    }

    @Test
    fun 전환_실패는_안내만() = runTest {
        coEvery { groups.switchTo(2) } returns ApiResult.Failure(404, ApiError("NOT_FOUND", "찾을 수 없어요.", null))

        viewModel.events.test {
            viewModel.switchTo(2)
            assertEquals(MainEvent.Message(UiText.Plain("찾을 수 없어요.")), awaitItem())
        }
    }

    @Test
    fun 초대는_현재_그룹_코드를_공유한다() = runTest {
        coEvery { groups.issueInvite(1) } returns ApiResult.Success(Invite("K7MPQ2XA", "2026-11-24T00:00:00Z"))

        viewModel.events.test {
            viewModel.invite()
            assertEquals(MainEvent.ShareInvite("우리 가족", "K7MPQ2XA"), awaitItem())
        }
    }
}
