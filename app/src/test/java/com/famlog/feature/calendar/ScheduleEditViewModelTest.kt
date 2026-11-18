package com.famlog.feature.calendar

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.ScheduleRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Reminder
import com.famlog.core.model.RepeatFreq
import com.famlog.core.model.Schedule
import com.famlog.core.model.ScheduleConflict
import com.famlog.core.model.ScheduleDraft
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleEditViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val seoul = ZoneId.of("Asia/Seoul")

    /** 2026-11-18 (수) 서울 14:20 */
    private val clock = Clock.fixed(Instant.parse("2026-11-18T05:20:00Z"), seoul)
    private val members = listOf(
        Member(ME, "윤서", GroupRole.MEMBER, null, "막내", null),
        Member(DAD, "김아빠", GroupRole.OWNER, null, "아빠", null),
    )
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { currentGroupId } returns MutableStateFlow(GROUP)
        every { details } returns MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, members)))
    }
    private val existing = Schedule(
        id = 7, groupId = GROUP, title = "아빠 치과", allDay = false,
        startsAt = Instant.parse("2026-11-21T01:00:00Z"), endsAt = Instant.parse("2026-11-21T02:00:00Z"),
        startDate = LocalDate.of(2026, 11, 21), endDate = LocalDate.of(2026, 11, 21),
        participantIds = listOf(DAD), rrule = "FREQ=WEEKLY;INTERVAL=2;COUNT=5", memo = "보험증",
        reminders = listOf(Reminder.MIN_30), createdBy = DAD, version = 3,
    )

    private val draft = slot<ScheduleDraft>()
    private val schedules = mockk<ScheduleRepository> {
        coEvery { conflicts(any(), any(), any(), any(), any()) } returns ApiResult.Success(emptyList())
        coEvery { create(GROUP, capture(draft)) } answers { ApiResult.Success(existing.copy(id = 99)) }
        coEvery { detail(7) } returns ApiResult.Success(existing)
    }


    private fun newViewModel(date: String? = null) = ScheduleEditViewModel(
        SavedStateHandle(mapOf("scheduleId" to 0L, "date" to date)), schedules, groups, FakeSessionStore(userId = ME), clock,
    )

    private fun editViewModel() = ScheduleEditViewModel(
        SavedStateHandle(mapOf("scheduleId" to 7L)), schedules, groups, FakeSessionStore(userId = ME), clock,
    )

    @Test
    fun 오늘_등록은_다음_정시부터_1시간_참여자는_나_알림은_기본값() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        val form = viewModel.uiState.value.form
        assertEquals(LocalDate.of(2026, 11, 18), form.startDate)
        assertEquals(LocalTime.of(15, 0), form.startTime)
        assertEquals(LocalTime.of(16, 0), form.endTime)
        assertEquals(setOf(ME), form.participants)
        assertEquals(listOf(Reminder.PREV_DAY_20, Reminder.HOUR_1), form.reminders)
    }

    @Test
    fun 다른_날_등록은_오전_10시() = runTest {
        val viewModel = newViewModel(date = "2026-11-25")
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        assertEquals(LocalDate.of(2026, 11, 25), viewModel.uiState.value.form.startDate)
        assertEquals(LocalTime.of(10, 0), viewModel.uiState.value.form.startTime)
    }

    @Test
    fun 제목이_없으면_저장하지_않고_오류를_보인다() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.save()

        assertTrue(viewModel.uiState.value.showErrors)
        coVerify(exactly = 0) { schedules.create(any(), any()) }
    }

    @Test
    fun 종료가_시작보다_빠르거나_참여자가_없으면_저장할_수_없다() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        viewModel.onTitleChange("저녁")

        viewModel.onEndTimeChange(LocalTime.of(14, 0))
        assertTrue(viewModel.uiState.value.form.endBeforeStart)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onEndTimeChange(LocalTime.of(17, 0))
        viewModel.toggleParticipant(ME)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun 시작을_옮기면_길이를_지킨_채_끝도_옮긴다() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        viewModel.onEndTimeChange(LocalTime.of(17, 0)) // 15:00~17:00

        viewModel.onStartTimeChange(LocalTime.of(23, 0))

        val form = viewModel.uiState.value.form
        assertEquals(LocalDate.of(2026, 11, 19), form.endDate)
        assertEquals(LocalTime.of(1, 0), form.endTime)
    }

    @Test
    fun 종일로_바꾸면_알림은_전날_오후_8시만_남는다() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.onAllDayChange(true)
        assertEquals(listOf(Reminder.PREV_DAY_20), viewModel.uiState.value.form.reminders)
        viewModel.toggleReminder(Reminder.MIN_10)
        assertEquals(listOf(Reminder.PREV_DAY_20), viewModel.uiState.value.form.reminders)

        viewModel.onAllDayChange(false)
        assertEquals(listOf(Reminder.PREV_DAY_20, Reminder.HOUR_1), viewModel.uiState.value.form.reminders)
    }

    @Test
    fun 알림은_3개까지() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.events.test {
            viewModel.toggleReminder(Reminder.MIN_10)
            viewModel.toggleReminder(Reminder.MIN_30)
            assertEquals(ScheduleEditEvent.Message(UiText.Res(R.string.schedule_reminder_max)), awaitItem())
        }
        assertEquals(listOf(Reminder.PREV_DAY_20, Reminder.HOUR_1, Reminder.MIN_10), viewModel.uiState.value.form.reminders)
    }

    @Test
    fun 등록하면_서울_시각과_반복_규칙을_보낸다() = runTest {
        val viewModel = newViewModel(date = "2026-11-25")
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        viewModel.onTitleChange("  할머니 생신 저녁 ")
        viewModel.toggleParticipant(DAD)
        viewModel.onRepeatChange(RepeatFreq.YEARLY)
        viewModel.onRepeatUntilChange(LocalDate.of(2030, 12, 31))

        viewModel.events.test {
            viewModel.save()
            assertEquals(ScheduleEditEvent.Saved(99), awaitItem())
        }

        val sent = draft.captured
        assertEquals("할머니 생신 저녁", sent.title)
        assertEquals(Instant.parse("2026-11-25T01:00:00Z"), sent.startsAt)
        assertEquals(setOf(ME, DAD), sent.participantIds.toSet())
        assertEquals("FREQ=YEARLY;UNTIL=20301231", sent.rrule)
        assertNull(sent.startDate)
    }

    @Test
    fun 수정은_기존_값을_채우고_반복_간격과_횟수를_지킨다() = runTest {
        val sent = slot<ScheduleDraft>()
        coEvery { schedules.update(existing, capture(sent)) } returns ApiResult.Success(existing)
        val viewModel = editViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        val form = viewModel.uiState.value.form
        assertTrue(viewModel.uiState.value.isEdit)
        assertEquals("아빠 치과", form.title)
        assertEquals(LocalTime.of(10, 0), form.startTime)
        assertEquals(RepeatFreq.WEEKLY, form.repeatFreq)

        viewModel.onTitleChange("아빠 치과 (변경)")
        viewModel.save()

        assertEquals("FREQ=WEEKLY;INTERVAL=2;COUNT=5", sent.captured.rrule)
        assertEquals(listOf(Reminder.MIN_30), sent.captured.reminders)
    }

    @Test
    fun 다른_가족이_먼저_고쳤으면_안내하고_최신_내용을_다시_불러온다() = runTest {
        coEvery { schedules.update(any(), any()) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.VERSION_CONFLICT, "충돌", null))
        val viewModel = editViewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        viewModel.onTitleChange("내가 고친 제목")

        viewModel.events.test {
            viewModel.save()
            assertEquals(ScheduleEditEvent.Message(UiText.Res(R.string.schedule_version_conflict)), awaitItem())
        }

        coVerify(exactly = 2) { schedules.detail(7) }
        assertEquals("아빠 치과", viewModel.uiState.value.form.title)
    }

    @Test
    fun 시간과_참여자를_바꾸면_잠시_뒤_겹치는_일정을_묻는다() = runTest {
        coEvery { schedules.conflicts(GROUP, any(), any(), any(), null) } returns
            ApiResult.Success(listOf(ScheduleConflict(5, "치과", false, listOf(DAD))))
        val viewModel = newViewModel(date = "2026-11-25")
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.toggleParticipant(DAD)
        advanceTimeBy(500)

        coVerify {
            schedules.conflicts(
                GROUP,
                Instant.parse("2026-11-25T01:00:00Z"),
                Instant.parse("2026-11-25T02:00:00Z"),
                match { it.toSet() == setOf(ME, DAD) },
                null,
            )
        }
        assertEquals("치과", viewModel.uiState.value.conflicts.single().title)
        assertEquals("아빠", viewModel.uiState.value.nameOf(DAD))
    }

    private companion object {
        const val GROUP = 1L
        const val ME = 10L
        const val DAD = 11L
    }
}
