package com.famlog.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.famlog.R
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.Member
import com.famlog.core.model.Occurrence
import com.famlog.core.model.Reminder
import com.famlog.core.model.Repeat
import com.famlog.core.model.RepeatFreq
import com.famlog.feature.chat.SeoulZone
import com.famlog.feature.chat.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val dayWithYearFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val shortDayFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

/** "11월 21일 (토)", 다른 해면 연도를 붙인다 */
fun formatDay(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) dayFormatter.format(date) else dayWithYearFormatter.format(date)

/**
 * 일정 시간 한 줄. 종일 "종일", 하루 안 "오후 6:00 ~ 오후 7:00", 여러 날이면 날짜를 붙인다
 * ("11월 21일 오후 6:00 ~ 11월 22일 오전 9:00", "11월 21일 ~ 11월 23일 종일").
 */
fun formatTimeRange(
    allDay: Boolean,
    startsAt: Instant,
    endsAt: Instant,
    startDate: LocalDate,
    endDate: LocalDate,
    allDayLabel: String,
): String {
    if (allDay) {
        return if (startDate == endDate) allDayLabel
        else "${shortDayFormatter.format(startDate)} ~ ${shortDayFormatter.format(endDate)} $allDayLabel"
    }
    val start = startsAt.atZone(SeoulZone)
    val end = endsAt.atZone(SeoulZone)
    return if (start.toLocalDate() == end.toLocalDate() || startDate == endDate) {
        "${formatTime(startsAt)} ~ ${formatTime(endsAt)}"
    } else {
        "${shortDayFormatter.format(start)} ${formatTime(startsAt)} ~ ${shortDayFormatter.format(end)} ${formatTime(endsAt)}"
    }
}

@Composable
fun Occurrence.timeText(): String =
    formatTimeRange(allDay, startsAt, endsAt, startDate, endDate, stringResource(R.string.schedule_all_day))

/** 서버 calendar_color("#RRGGBB"). 없거나 형식이 틀리면 구성원 순서대로 팔레트 색 */
@Composable
fun memberColor(member: Member, members: List<Member>): Color {
    member.calendarColor?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let {
        return Color(0xFF000000 or it)
    }
    val palette = FamTheme.colors.memberPalette
    val index = members.indexOfFirst { it.userId == member.userId }.coerceAtLeast(0)
    return palette[index % palette.size]
}

@Composable
fun Reminder.label(): String = stringResource(
    when (this) {
        Reminder.PREV_DAY_20 -> R.string.schedule_reminder_prev_day_20
        Reminder.DAY_1 -> R.string.schedule_reminder_day_1
        Reminder.HOUR_1 -> R.string.schedule_reminder_hour_1
        Reminder.MIN_30 -> R.string.schedule_reminder_min_30
        Reminder.MIN_10 -> R.string.schedule_reminder_min_10
    },
)

@Composable
fun RepeatFreq?.label(): String = stringResource(
    when (this) {
        null -> R.string.schedule_repeat_none
        RepeatFreq.WEEKLY -> R.string.schedule_repeat_weekly
        RepeatFreq.MONTHLY -> R.string.schedule_repeat_monthly
        RepeatFreq.YEARLY -> R.string.schedule_repeat_yearly
    },
)

/** 상세 화면: "매주 · 12월 31일까지", 간격이 있으면 "2주마다" */
@Composable
fun Repeat.describe(today: LocalDate): String {
    val unit = when (freq) {
        RepeatFreq.WEEKLY -> "주"
        RepeatFreq.MONTHLY -> "개월"
        RepeatFreq.YEARLY -> "년"
    }
    val base = if (interval > 1) stringResource(R.string.schedule_repeat_interval, interval, unit) else freq.label()
    val end = when {
        until != null -> stringResource(R.string.schedule_repeat_until, formatDay(until, today))
        count != null -> stringResource(R.string.schedule_repeat_count, count)
        else -> null
    }
    return listOfNotNull(base, end).joinToString(" · ")
}
