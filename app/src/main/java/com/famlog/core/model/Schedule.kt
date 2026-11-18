package com.famlog.core.model

import java.time.Instant
import java.time.LocalDate

/** 일정 알림 시점 (SCH-03). 종일 일정은 [PREV_DAY_20]만 */
enum class Reminder {
    PREV_DAY_20, DAY_1, HOUR_1, MIN_30, MIN_10;

    companion object {
        fun of(value: String?): Reminder? = entries.firstOrNull { it.name == value }

        /** 서버 기본값: 시간 일정 [전날 20시, 1시간 전], 종일 [전날 20시] */
        fun defaults(allDay: Boolean): List<Reminder> = if (allDay) listOf(PREV_DAY_20) else listOf(PREV_DAY_20, HOUR_1)

        const val MAX = 3
    }
}

enum class RepeatFreq { WEEKLY, MONTHLY, YEARLY }

/**
 * 반복 규칙 (RFC 5545 RRULE 중 서버가 받는 것: FREQ·INTERVAL·UNTIL·COUNT).
 * 앱은 종류와 종료일만 고치지만, 다른 곳에서 만든 간격·횟수는 그대로 지켜 다시 보낸다.
 */
data class Repeat(
    val freq: RepeatFreq,
    val until: LocalDate? = null,
    val interval: Int = 1,
    val count: Int? = null,
) {
    fun toRrule(): String = buildString {
        append("FREQ=").append(freq.name)
        if (interval > 1) append(";INTERVAL=").append(interval)
        when {
            until != null -> append(";UNTIL=").append(until.toString().replace("-", ""))
            count != null -> append(";COUNT=").append(count)
        }
    }

    companion object {
        /** 알 수 없는 형식이면 null (반복 없음으로 보이지 않게 호출한 쪽이 원문을 지킨다) */
        fun parse(rrule: String?): Repeat? {
            if (rrule.isNullOrBlank()) return null
            val parts = rrule.removePrefix("RRULE:").split(';').mapNotNull {
                val (key, value) = it.split('=', limit = 2).takeIf { kv -> kv.size == 2 } ?: return@mapNotNull null
                key.uppercase() to value
            }.toMap()
            val freq = RepeatFreq.entries.firstOrNull { it.name == parts["FREQ"] } ?: return null
            val until = parts["UNTIL"]?.take(8)?.let {
                runCatching { LocalDate.of(it.substring(0, 4).toInt(), it.substring(4, 6).toInt(), it.substring(6, 8).toInt()) }
                    .getOrNull()
            }
            return Repeat(
                freq = freq,
                until = until,
                interval = parts["INTERVAL"]?.toIntOrNull() ?: 1,
                count = parts["COUNT"]?.toIntOrNull(),
            )
        }
    }
}

/**
 * 캘린더에 보이는 일정 한 회차. 반복 일정은 회차마다 하나(같은 [scheduleId]).
 * [startDate]~[endDate]는 서울 날짜(마지막 날 포함) — 월 격자의 점과 날짜별 목록에 쓴다.
 */
data class Occurrence(
    val scheduleId: Long,
    val title: String,
    val allDay: Boolean,
    val startsAt: Instant,
    val endsAt: Instant,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val participantIds: List<Long>,
    val createdBy: Long?,
    val recurring: Boolean,
) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)
}

/** 일정 시리즈 (상세·수정). 수정할 때 [version]을 함께 보낸다 */
data class Schedule(
    val id: Long,
    val groupId: Long,
    val title: String,
    val allDay: Boolean,
    val startsAt: Instant,
    val endsAt: Instant,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val participantIds: List<Long>,
    val rrule: String?,
    val memo: String?,
    val reminders: List<Reminder>,
    val createdBy: Long?,
    val version: Long,
) {
    val repeat: Repeat? get() = Repeat.parse(rrule)
}

/** 등록·수정 입력. 시간 일정은 [startsAt]·[endsAt], 종일은 [startDate]·[endDate](포함)만 쓴다 */
data class ScheduleDraft(
    val title: String,
    val allDay: Boolean,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val participantIds: List<Long>,
    val rrule: String?,
    val memo: String?,
    val reminders: List<Reminder>,
)

/** 겹치는 일정 (SCH-05). [userIds]는 물어본 구성원 중 그 일정 참여자 */
data class ScheduleConflict(
    val scheduleId: Long,
    val title: String,
    val allDay: Boolean,
    val userIds: List<Long>,
)

data class Holiday(val date: LocalDate, val name: String, val substitute: Boolean)
