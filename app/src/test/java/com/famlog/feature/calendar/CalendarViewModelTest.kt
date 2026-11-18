package com.famlog.feature.calendar

import com.famlog.core.data.GroupRepository
import com.famlog.core.data.ScheduleRepository
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Holiday
import com.famlog.core.model.Member
import com.famlog.core.model.Occurrence
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val selectedGroup = MutableStateFlow<Long?>(GROUP)
    private val changes = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    private val members = listOf(
        Member(DAD, "김아빠", GroupRole.OWNER, null, "아빠", "#14A3B8"),
        Member(MOM, "김엄마", GroupRole.MEMBER, null, "엄마", "#E5568D"),
    )
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { currentGroupId } returns selectedGroup
        every { details } returns MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.OWNER, members)))
    }
    private val schedules = mockk<ScheduleRepository> {
        every { changes } returns this@CalendarViewModelTest.changes
        coEvery { occurrences(any(), any(), any()) } returns ApiResult.Success(
            listOf(
                occurrence(1, MOM), occurrence(2, MOM), occurrence(3, MOM, day = 22),
                occurrence(4, DAD), occurrence(5, DAD, day = 25),
            ),
        )
        coEvery { holidays(any()) } returns ApiResult.Success(emptyList())
        coEvery { holidays(2026) } returns ApiResult.Success(listOf(Holiday(LocalDate.of(2026, 12, 25), "성탄절", false)))
    }

    private fun viewModel() = CalendarViewModel(schedules, groups, clock)

    @Test
    fun 현재_그룹의_월_격자_범위를_받고_오늘을_고른다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        coVerify { schedules.occurrences(GROUP, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 5)) }
        val state = viewModel.uiState.value
        assertEquals(YearMonth.of(2026, 11), state.month)
        assertEquals(LocalDate.of(2026, 11, 18), state.selectedDate)
        assertEquals("성탄절", state.holidays[LocalDate.of(2026, 12, 25)]?.name)
        assertEquals(members, state.members)
    }

    /** 인수 기준 SCH-02 */
    @Test
    fun 엄마_필터는_3개_전체는_5개() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        assertEquals(5, viewModel.uiState.value.visible.size)
        viewModel.toggleMember(MOM)
        assertEquals(3, viewModel.uiState.value.visible.size)
        viewModel.toggleMember(DAD)
        assertEquals(5, viewModel.uiState.value.visible.size)
        viewModel.toggleMember(MOM)
        viewModel.toggleMember(DAD)
        assertTrue(viewModel.uiState.value.selectedMembers.isEmpty())
        viewModel.toggleMember(MOM)
        viewModel.selectAll()
        assertEquals(5, viewModel.uiState.value.visible.size)
    }

    @Test
    fun 다른_달_날짜를_누르면_그_달로_넘어가_다시_받는다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.selectDate(LocalDate.of(2026, 12, 3))

        assertEquals(YearMonth.of(2026, 12), viewModel.uiState.value.month)
        assertEquals(LocalDate.of(2026, 12, 3), viewModel.uiState.value.selectedDate)
        coVerify { schedules.occurrences(GROUP, LocalDate.of(2026, 11, 29), LocalDate.of(2027, 1, 2)) }
        coVerify { schedules.holidays(2027) }
    }

    @Test
    fun 다음_달로_가면_그_달_1일을_고른다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.nextMonth()

        assertEquals(LocalDate.of(2026, 12, 1), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun 그룹을_바꾸면_필터를_풀고_새_그룹을_받는다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        viewModel.toggleMember(MOM)

        selectedGroup.value = OTHER_GROUP

        assertTrue(viewModel.uiState.value.selectedMembers.isEmpty())
        coVerify { schedules.occurrences(OTHER_GROUP, any(), any()) }
        coVerify { groups.detail(OTHER_GROUP) }
    }

    @Test
    fun 일정이_바뀌었다는_알림이_오면_다시_받는다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        changes.emit(GROUP)
        changes.emit(OTHER_GROUP)

        coVerify(exactly = 2) { schedules.occurrences(GROUP, any(), any()) }
    }

    @Test
    fun 받지_못하면_실패를_보이고_이전_일정은_남긴다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        coEvery { schedules.occurrences(any(), any(), any()) } returns ApiResult.NetworkError

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertEquals(5, viewModel.uiState.value.occurrences.size)
    }

    private fun occurrence(id: Long, participant: Long, day: Int = 18): Occurrence {
        val date = LocalDate.of(2026, 11, day)
        val start = date.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
        return Occurrence(id, "일정 $id", false, start, start.plusSeconds(3600), date, date, listOf(participant), participant, false)
    }

    private companion object {
        const val GROUP = 1L
        const val OTHER_GROUP = 2L
        const val DAD = 10L
        const val MOM = 11L
    }
}
