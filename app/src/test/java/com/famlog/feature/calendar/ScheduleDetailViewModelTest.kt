package com.famlog.feature.calendar

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.ScheduleRepository
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Schedule
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ScheduleDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val changes = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    private val schedule = Schedule(
        id = 7, groupId = GROUP, title = "치과", allDay = false,
        startsAt = Instant.parse("2026-11-21T01:00:00Z"), endsAt = Instant.parse("2026-11-21T02:00:00Z"),
        startDate = LocalDate.of(2026, 11, 21), endDate = LocalDate.of(2026, 11, 21),
        participantIds = listOf(DAD), rrule = null, memo = null, reminders = emptyList(), createdBy = DAD, version = 1,
    )
    private val schedules = mockk<ScheduleRepository> {
        every { changes } returns this@ScheduleDetailViewModelTest.changes
        coEvery { detail(7) } returns ApiResult.Success(schedule)
    }
    private fun groups(myRole: GroupRole) = mockk<GroupRepository>(relaxed = true) {
        every { details } returns MutableStateFlow(
            mapOf(GROUP to GroupDetail(GROUP, "우리 가족", myRole, listOf(Member(DAD, "김아빠", GroupRole.OWNER, null, "아빠", null)))),
        )
    }

    private fun viewModel(me: Long, myRole: GroupRole = GroupRole.MEMBER) =
        ScheduleDetailViewModel(SavedStateHandle(mapOf("scheduleId" to 7L)), schedules, groups(myRole), FakeSessionStore(userId = me))

    @Test
    fun 등록한_사람이나_관리자만_수정_삭제할_수_있다() = runTest {
        val mine = viewModel(me = DAD)
        val other = viewModel(me = KID)
        val admin = viewModel(me = KID, myRole = GroupRole.ADMIN)
        listOf(mine, other, admin).forEach { vm -> backgroundScope.launch(mainRule.dispatcher) { vm.uiState.collect {} } }

        assertTrue(mine.uiState.value.canEdit)
        assertFalse(other.uiState.value.canEdit)
        assertTrue(admin.uiState.value.canEdit)
        assertEquals("아빠", mine.uiState.value.nameOf(DAD))
    }

    @Test
    fun 삭제하면_다시_받지_않고_닫기만_한다() = runTest {
        coEvery { schedules.delete(schedule) } coAnswers {
            changes.emit(GROUP)
            ApiResult.Success(Unit)
        }
        val viewModel = viewModel(me = DAD)
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.events.test {
            viewModel.delete()
            assertEquals(ScheduleDetailEvent.Closed, awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 1) { schedules.detail(7) }
    }

    @Test
    fun 없는_일정이면_안내하고_닫는다() = runTest {
        coEvery { schedules.detail(7) } returns ApiResult.Failure(404, ApiError("NOT_FOUND", "찾을 수 없어요.", null))
        val viewModel = viewModel(me = DAD)

        viewModel.events.test {
            assertTrue(awaitItem() is ScheduleDetailEvent.Message)
            assertEquals(ScheduleDetailEvent.Closed, awaitItem())
        }
    }

    private companion object {
        const val GROUP = 1L
        const val DAD = 10L
        const val KID = 12L
    }
}
