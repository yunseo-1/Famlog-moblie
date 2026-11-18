package com.famlog.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.ScheduleRepository
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Holiday
import com.famlog.core.model.Member
import com.famlog.core.model.Occurrence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class CalendarTab { SCHEDULE, TODO }

enum class CalendarMode { MONTH, LIST }

data class CalendarUiState(
    val groupId: Long? = null,
    val members: List<Member> = emptyList(),
    val tab: CalendarTab = CalendarTab.SCHEDULE,
    val mode: CalendarMode = CalendarMode.MONTH,
    /** 비었으면 [전체] */
    val selectedMembers: Set<Long> = emptySet(),
    val month: YearMonth = YearMonth.of(2000, 1),
    val selectedDate: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    /** 월 격자 범위의 모든 일정 (거르기 전) */
    val occurrences: List<Occurrence> = emptyList(),
    val holidays: Map<LocalDate, Holiday> = emptyMap(),
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
) {
    val grid: List<LocalDate> get() = monthGrid(month)

    val visible: List<Occurrence> get() = occurrences.filterByMembers(selectedMembers)

    val selectedDayItems: List<Occurrence> get() = visible.on(selectedDate)

    val listSections: List<Pair<LocalDate, List<Occurrence>>> get() = visible.sectionsIn(month)

    fun member(userId: Long): Member? = members.firstOrNull { it.userId == userId }

    fun dots(date: LocalDate): List<Member> = visible.dotMembers(date, members, selectedMembers)
}

/** SCR-20 캘린더 탭 (SCH-02·04). 현재 가족 그룹의 일정만 보인다 (CLAUDE.md 3장) */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val groupRepository: GroupRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(initialState())
    private var loadJob: Job? = null

    val uiState: StateFlow<CalendarUiState> = combine(local, groupRepository.details) { state, details ->
        val members = state.groupId?.let { details[it]?.members }.orEmpty()
        state.copy(members = members, selectedMembers = state.selectedMembers.filterTo(mutableSetOf()) { id ->
            members.isEmpty() || members.any { it.userId == id }
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // 그룹을 바꾸면 필터를 풀고 그 그룹 일정을 받는다
        viewModelScope.launch {
            groupRepository.currentGroupId.filterNotNull().distinctUntilChanged().collect { groupId ->
                local.update { it.copy(groupId = groupId, selectedMembers = emptySet(), occurrences = emptyList()) }
                launch { groupRepository.detail(groupId) }
                load()
            }
        }
        viewModelScope.launch {
            scheduleRepository.changes.collect { groupId -> if (groupId == local.value.groupId) load() }
        }
    }

    /** 화면에 돌아올 때마다: 다른 가족이 바꾼 일정을 반영한다 */
    fun refresh() {
        if (local.value.groupId != null) load()
    }

    fun selectTab(tab: CalendarTab) = local.update { it.copy(tab = tab) }

    fun selectMode(mode: CalendarMode) = local.update { it.copy(mode = mode) }

    fun selectAll() = local.update { it.copy(selectedMembers = emptySet()) }

    fun toggleMember(userId: Long) = local.update { state ->
        val next = if (userId in state.selectedMembers) state.selectedMembers - userId else state.selectedMembers + userId
        state.copy(selectedMembers = next)
    }

    fun showMonth(month: YearMonth) {
        val today = LocalDate.now(clock)
        local.update {
            it.copy(month = month, selectedDate = if (YearMonth.from(today) == month) today else month.atDay(1))
        }
        load()
    }

    fun previousMonth() = showMonth(local.value.month.minusMonths(1))

    fun nextMonth() = showMonth(local.value.month.plusMonths(1))

    /** 격자에서 앞뒤 달의 날짜를 누르면 그 달로 넘어간다 */
    fun selectDate(date: LocalDate) {
        if (YearMonth.from(date) != local.value.month) {
            local.update { it.copy(month = YearMonth.from(date), selectedDate = date) }
            load()
        } else {
            local.update { it.copy(selectedDate = date) }
        }
    }

    private fun load() {
        val state = local.value
        val groupId = state.groupId ?: return
        val grid = monthGrid(state.month)
        val from = grid.first()
        val to = grid.last()
        loadJob?.cancel()
        local.update { it.copy(loading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            val holidays = (from.year..to.year).map { year -> async { scheduleRepository.holidays(year) } }
            val result = scheduleRepository.occurrences(groupId, from, to)
            val holidayMap = holidays.flatMap { (it.await() as? ApiResult.Success)?.value.orEmpty() }
                .associateBy { it.date }
            local.update {
                it.copy(
                    occurrences = (result as? ApiResult.Success)?.value ?: it.occurrences,
                    holidays = it.holidays + holidayMap,
                    loading = false,
                    loadFailed = result !is ApiResult.Success,
                )
            }
        }
    }

    private fun initialState(): CalendarUiState {
        val today = LocalDate.now(clock)
        return CalendarUiState(month = YearMonth.from(today), selectedDate = today, today = today)
    }
}
