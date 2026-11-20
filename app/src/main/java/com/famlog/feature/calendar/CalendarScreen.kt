package com.famlog.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.component.FamSegmented
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.CandidateType
import com.famlog.core.model.Holiday
import com.famlog.core.model.ItemSource
import com.famlog.core.model.Member
import com.famlog.core.model.Occurrence
import com.famlog.feature.chat.ChatSourceTag
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** SCR-20 캘린더 탭 (SCH-02·04). [일정 | 할 일] 전환, 구성원 필터, 월·목록 보기 */
@Composable
fun CalendarScreen(
    onOpenSchedule: (Long) -> Unit,
    onAddSchedule: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSource: (groupId: Long, source: ItemSource) -> Unit = { _, _ -> },
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    CalendarContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onSelectAll = viewModel::selectAll,
        onToggleMember = viewModel::toggleMember,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectMode = viewModel::selectMode,
        onSelectDate = viewModel::selectDate,
        onRetry = viewModel::refresh,
        onOpenSchedule = onOpenSchedule,
        onAddSchedule = { onAddSchedule(state.selectedDate) },
        modifier = modifier,
        todoContent = { TodoTab(onOpenSource = onOpenSource) },
    )
}

@Composable
fun CalendarContent(
    state: CalendarUiState,
    onSelectTab: (CalendarTab) -> Unit,
    onSelectAll: () -> Unit,
    onToggleMember: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMode: (CalendarMode) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onAddSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    /** [할 일] 탭 내용 (자기 ViewModel을 가진다) */
    todoContent: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(FamTheme.colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamSegmented(
                options = CalendarTab.entries,
                selected = state.tab,
                label = { stringResource(if (it == CalendarTab.SCHEDULE) R.string.calendar_tab_schedule else R.string.calendar_tab_todo) },
                onSelect = onSelectTab,
                modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
            )
            when (state.tab) {
                CalendarTab.TODO -> todoContent()
                CalendarTab.SCHEDULE -> ScheduleTab(
                    state = state,
                    onSelectAll = onSelectAll,
                    onToggleMember = onToggleMember,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectMode = onSelectMode,
                    onSelectDate = onSelectDate,
                    onRetry = onRetry,
                    onOpenSchedule = onOpenSchedule,
                )
            }
        }
        if (state.tab == CalendarTab.SCHEDULE) {
            FloatingActionButton(
                onClick = onAddSchedule,
                containerColor = FamTheme.colors.brandPrimary,
                contentColor = FamTheme.colors.textOnBrand,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(FamDimens.lg),
            ) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.calendar_add_schedule))
            }
        }
    }
}

@Composable
private fun ScheduleTab(
    state: CalendarUiState,
    onSelectAll: () -> Unit,
    onToggleMember: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMode: (CalendarMode) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // FAB에 마지막 일정이 가리지 않게
        contentPadding = PaddingValues(bottom = FamDimens.fabSize + FamDimens.xxxl),
    ) {
        item(key = "filters") {
            MemberFilter(state.members, state.selectedMembers, onSelectAll, onToggleMember)
        }
        item(key = "header") {
            MonthHeader(state.month, state.mode, onPreviousMonth, onNextMonth, onSelectMode)
        }
        if (state.loadFailed) {
            item(key = "failed") { LoadFailed(onRetry) }
        }
        when (state.mode) {
            CalendarMode.MONTH -> {
                item(key = "grid") {
                    MonthGrid(state, onSelectDate)
                    HorizontalDivider(color = FamTheme.colors.borderDefault, modifier = Modifier.padding(top = FamDimens.sm))
                }
                daySection(
                    date = state.selectedDate,
                    holiday = state.holidays[state.selectedDate],
                    items = state.selectedDayItems,
                    state = state,
                    emptyText = R.string.calendar_empty_day,
                    onOpenSchedule = onOpenSchedule,
                )
            }
            CalendarMode.LIST -> {
                val sections = state.listSections
                if (sections.isEmpty() && !state.loading) {
                    item(key = "empty") { EmptyText(R.string.calendar_empty_month) }
                }
                sections.forEach { (date, items) ->
                    daySection(date, state.holidays[date], items, state, R.string.calendar_empty_day, onOpenSchedule)
                }
            }
        }
    }
}

/** [전체] + 구성원 칩 (여러 명 선택). 점 색 = 구성원 캘린더 색, 이름을 함께 보인다 (색만으로 구분 금지) */
@Composable
private fun MemberFilter(
    members: List<Member>,
    selected: Set<Long>,
    onSelectAll: () -> Unit,
    onToggle: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = FamDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
    ) {
        item(key = "all") {
            FamChip(text = stringResource(R.string.calendar_filter_all), selected = selected.isEmpty(), onClick = onSelectAll)
        }
        items(members, key = { it.userId }) { member ->
            FamChip(
                text = member.displayName,
                selected = member.userId in selected,
                onClick = { onToggle(member.userId) },
                dotColor = memberColor(member, members),
                role = Role.Checkbox,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    mode: CalendarMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectMode: (CalendarMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = FamDimens.screenPadding, end = FamDimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.calendar_month_title, month.year, month.monthValue),
            style = FamTheme.type.titleMedium,
            color = FamTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        IconButton(onClick = onPrevious) {
            Icon(
                painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.calendar_previous_month),
                tint = FamTheme.colors.textSecondary,
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.calendar_next_month),
                tint = FamTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onSelectMode(if (mode == CalendarMode.MONTH) CalendarMode.LIST else CalendarMode.MONTH) }) {
            Text(
                text = stringResource(if (mode == CalendarMode.MONTH) R.string.calendar_mode_list else R.string.calendar_mode_month),
                style = FamTheme.type.label,
                color = FamTheme.colors.brandPrimary,
            )
        }
    }
}

/** 요일 행 + 주 × 7칸 (칸 높이 44). 일요일 숫자 text/tertiary, 공휴일은 빨간 날 (SCH-04) */
@Composable
private fun MonthGrid(state: CalendarUiState, onSelectDate: (LocalDate) -> Unit) {
    val colors = FamTheme.colors
    val week = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
    Column(Modifier.padding(horizontal = FamDimens.sm)) {
        Row(Modifier.fillMaxWidth().padding(vertical = FamDimens.xs)) {
            week.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    style = FamTheme.type.caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        state.grid.chunked(7).forEach { days ->
            Row(Modifier.fillMaxWidth()) {
                days.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == state.month,
                        selected = date == state.selectedDate,
                        today = date == state.today,
                        holiday = state.holidays[date],
                        dots = state.dots(date),
                        itemCount = state.visible.count { it.covers(date) },
                        members = state.members,
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    holiday: Holiday?,
    dots: List<Member>,
    itemCount: Int,
    members: List<Member>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    val numberColor = when {
        selected -> colors.textOnBrand
        holiday != null -> colors.statusDanger
        !inMonth || date.dayOfWeek == DayOfWeek.SUNDAY -> colors.textTertiary
        else -> colors.textPrimary
    }
    val description = stringResource(
        R.string.calendar_day_description,
        listOfNotNull(formatDay(date, date), holiday?.name).joinToString(", "),
        itemCount,
    )
    Column(
        modifier = modifier
            .height(48.dp)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(if (selected) colors.brandPrimary else colors.bgDefault, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = if (today || selected) FamTheme.type.label else FamTheme.type.bodyMedium,
                color = if (inMonth || selected) numberColor else numberColor.copy(alpha = 0.5f),
            )
        }
        Row(
            modifier = Modifier.height(9.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            dots.forEach { member ->
                Box(Modifier.size(5.dp).background(memberColor(member, members), CircleShape))
            }
        }
    }
}

/** 날짜 제목 + 그날 일정. 공휴일이면 이름을 빨간 글씨로 */
private fun LazyListScope.daySection(
    date: LocalDate,
    holiday: Holiday?,
    items: List<Occurrence>,
    state: CalendarUiState,
    emptyText: Int,
    onOpenSchedule: (Long) -> Unit,
) {
    item(key = "day:$date") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FamDimens.screenPadding)
                .padding(top = FamDimens.lg, bottom = FamDimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
        ) {
            Text(
                text = formatDay(date, state.today),
                style = FamTheme.type.bodyMediumStrong,
                color = FamTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (holiday != null) {
                Text(text = holiday.name, style = FamTheme.type.caption, color = FamTheme.colors.statusDanger)
            }
        }
    }
    if (items.isEmpty()) {
        item(key = "empty:$date") { EmptyText(emptyText) }
    }
    items.forEach { occurrence ->
        item(key = "event:$date:${occurrence.scheduleId}:${occurrence.startsAt}") {
            EventRow(occurrence, state, onClick = { onOpenSchedule(occurrence.scheduleId) })
        }
    }
}

/** Figma Event Row: 색 막대 4×40(일정 주인 = 첫 참여자 색) / 제목 + 메타(시간 · 참여자 이름) */
@Composable
private fun EventRow(occurrence: Occurrence, state: CalendarUiState, onClick: () -> Unit) {
    val colors = FamTheme.colors
    val participants = state.members.filter { it.userId in occurrence.participantIds }
    val barColor = participants.firstOrNull()?.let { memberColor(it, state.members) } ?: colors.categorySchedule
    val meta = buildList {
        add(occurrence.timeText())
        if (participants.isNotEmpty()) add(participants.joinToString(" ") { it.displayName })
        if (occurrence.recurring) add(stringResource(R.string.calendar_recurring))
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        Box(Modifier.width(4.dp).height(40.dp).background(barColor, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(
                text = occurrence.title,
                style = FamTheme.type.bodyMediumStrong,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = FamTheme.type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 후보 승인으로 생긴 일정만 (Figma 03 Event Row)
        if (occurrence.source != null) ChatSourceTag(CandidateType.SCHEDULE)
    }
}

@Composable
private fun EmptyText(text: Int) {
    Text(
        text = stringResource(text),
        style = FamTheme.type.bodyMedium,
        color = FamTheme.colors.textTertiary,
        modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
    )
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.calendar_load_failed),
            style = FamTheme.type.bodyMedium,
            color = FamTheme.colors.statusDanger,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.calendar_retry), style = FamTheme.type.label, color = FamTheme.colors.brandPrimary)
        }
    }
}
