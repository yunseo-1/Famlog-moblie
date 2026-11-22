package com.famlog.feature.settings

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.NotificationSettingsRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.NotificationSettings
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

/** 알림 설정 (SET-02) */
class NotificationSettingsViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = mockk<NotificationSettingsRepository>()
    private val groupList = MutableStateFlow(listOf(GroupSummary(1, "우리가족", 2, GroupRole.OWNER), GroupSummary(2, "외가", 1, GroupRole.OWNER)))
    private val groups = mockk<GroupRepository> { every { this@mockk.groups } returns groupList }
    private val defaults = NotificationSettings()

    private fun loaded(): NotificationSettingsViewModel {
        coEvery { repository.settings() } returns ApiResult.Success(defaults)
        coEvery { repository.groupMuted(1) } returns ApiResult.Success(false)
        coEvery { repository.groupMuted(2) } returns ApiResult.NetworkError
        return NotificationSettingsViewModel(repository, groups)
    }

    @Test
    fun 들어오면_설정과_그룹별_알림을_받는다() = runTest(mainRule.dispatcher) {
        val state = loaded().state.value

        assertFalse(state.loading)
        assertEquals(defaults, state.settings)
        assertEquals(listOf(GroupNotification(1, "우리가족", false), GroupNotification(2, "외가", null)), state.groups)
    }

    @Test
    fun 그룹_목록이_비어_있으면_새로_받는다() = runTest(mainRule.dispatcher) {
        groupList.value = emptyList()
        coEvery { groups.refreshGroups() } returns ApiResult.Success(listOf(GroupSummary(3, "친가", 3, GroupRole.MEMBER)))
        coEvery { repository.groupMuted(3) } returns ApiResult.Success(true)
        coEvery { repository.settings() } returns ApiResult.Success(defaults)

        val state = NotificationSettingsViewModel(repository, groups).state.value

        assertEquals(listOf(GroupNotification(3, "친가", true)), state.groups)
    }

    @Test
    fun 설정을_못_받으면_다시_시도할_수_있다() = runTest(mainRule.dispatcher) {
        coEvery { repository.settings() } returns ApiResult.NetworkError

        assertTrue(NotificationSettingsViewModel(repository, groups).state.value.loadFailed)
    }

    @Test
    fun 바꾸면_저장하고_실패하면_되돌리고_알린다() = runTest(mainRule.dispatcher) {
        val viewModel = loaded()
        coEvery { repository.setPreviewHidden(true) } returns ApiResult.Success(defaults.copy(previewHidden = true))
        coEvery { repository.setQuietHours(true) } returns ApiResult.NetworkError

        viewModel.setPreviewHidden(true)
        assertTrue(viewModel.state.value.settings.previewHidden)

        viewModel.events.test {
            viewModel.setQuietHours(true)
            assertEquals(UiText.Res(R.string.error_network), awaitItem())
        }
        assertFalse(viewModel.state.value.settings.quietHoursEnabled)
    }

    @Test
    fun 방해_금지_시각은_하나만_바꿔도_시작과_끝을_함께_보내고_같으면_막는다() = runTest(mainRule.dispatcher) {
        val viewModel = loaded()
        coEvery { repository.setQuietTimes(LocalTime.of(23, 0), LocalTime.of(7, 0)) } returns
            ApiResult.Success(defaults.copy(quietStart = LocalTime.of(23, 0)))

        viewModel.setQuietStart(LocalTime.of(23, 0))
        assertEquals(LocalTime.of(23, 0), viewModel.state.value.settings.quietStart)

        viewModel.events.test {
            viewModel.setQuietEnd(LocalTime.of(23, 0))
            assertEquals(UiText.Res(R.string.notif_quiet_same), awaitItem())
        }
        coVerify(exactly = 1) { repository.setQuietTimes(any(), any()) }
        assertEquals(LocalTime.of(7, 0), viewModel.state.value.settings.quietEnd)
    }

    @Test
    fun 그룹_알림을_끄면_저장하고_실패하면_되돌린다() = runTest(mainRule.dispatcher) {
        val viewModel = loaded()
        coEvery { repository.setGroupMuted(1, true) } returns ApiResult.Success(true)
        coEvery { repository.setGroupMuted(1, false) } returns ApiResult.Failure(403, ApiError("GROUP_FORBIDDEN", "그룹에 속해 있지 않아요.", null))

        viewModel.setGroupEnabled(1, enabled = false)
        assertEquals(true, viewModel.state.value.groups.first().muted)

        viewModel.events.test {
            viewModel.setGroupEnabled(1, enabled = true)
            assertEquals(UiText.Plain("그룹에 속해 있지 않아요."), awaitItem())
        }
        assertEquals(true, viewModel.state.value.groups.first().muted)
    }
}
