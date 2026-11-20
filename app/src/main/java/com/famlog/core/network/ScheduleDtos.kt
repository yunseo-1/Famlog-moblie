package com.famlog.core.network

import com.famlog.core.model.Holiday
import com.famlog.core.model.Occurrence
import com.famlog.core.model.Reminder
import com.famlog.core.model.Schedule
import com.famlog.core.model.ScheduleConflict
import com.famlog.core.model.ScheduleDraft
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 일정 시각은 서울 기준 +09:00으로 주고받는다 (CLAUDE.md 3장) */
private val SeoulZone: ZoneId = ZoneId.of("Asia/Seoul")
private val offsetFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

fun Instant.toServerTime(): String = offsetFormatter.format(atZone(SeoulZone))

@Serializable
data class ScheduleRequest(
    val title: String,
    val allDay: Boolean,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val participantIds: List<Long>,
    val rrule: String? = null,
    val memo: String? = null,
    /** 빈 목록이면 알림 없음. 키를 빼면 서버 기본값이라 앱은 늘 보낸다 */
    val reminders: List<String>,
    val version: Long? = null,
    /** 이 그룹 장소 (아니면 400). 수정에서 빼면 연결이 비워지므로 기존 값을 다시 보낸다 */
    val placeId: Long? = null,
)

@Serializable
data class OccurrenceResponse(
    val scheduleId: Long,
    val title: String,
    val allDay: Boolean,
    val startsAt: String,
    val endsAt: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val participantIds: List<Long> = emptyList(),
    val createdBy: Long? = null,
    val recurring: Boolean = false,
    val source: SourceResponse? = null,
)

@Serializable
data class OccurrenceListResponse(val items: List<OccurrenceResponse>)

@Serializable
data class ScheduleResponse(
    val id: Long,
    val groupId: Long,
    val title: String,
    val allDay: Boolean,
    val startsAt: String,
    val endsAt: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val participantIds: List<Long> = emptyList(),
    val rrule: String? = null,
    val memo: String? = null,
    val reminders: List<String> = emptyList(),
    val createdBy: Long? = null,
    val version: Long,
    val place: PlaceSummaryResponse? = null,
    val source: SourceResponse? = null,
)

@Serializable
data class ConflictResponse(
    val scheduleId: Long,
    val title: String,
    val allDay: Boolean,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val userIds: List<Long> = emptyList(),
)

@Serializable
data class ConflictListResponse(val items: List<ConflictResponse>)

@Serializable
data class HolidayResponse(val date: String, val name: String, val substitute: Boolean = false)

@Serializable
data class HolidayListResponse(val items: List<HolidayResponse>)

fun ScheduleDraft.toRequest(version: Long? = null) = ScheduleRequest(
    title = title,
    allDay = allDay,
    startsAt = if (allDay) null else startsAt?.toServerTime(),
    endsAt = if (allDay) null else endsAt?.toServerTime(),
    startDate = if (allDay) startDate?.toString() else null,
    endDate = if (allDay) endDate?.toString() else null,
    participantIds = participantIds,
    rrule = rrule,
    memo = memo?.takeIf { it.isNotBlank() },
    reminders = reminders.map { it.name },
    version = version,
    placeId = placeId,
)

fun OccurrenceResponse.toModel(): Occurrence {
    val start = parseServerTime(startsAt)
    val end = parseServerTime(endsAt)
    val (startDay, endDay) = seoulDates(allDay, start, end, startDate, endDate)
    return Occurrence(
        scheduleId, title, allDay, start, end, startDay, endDay, participantIds, createdBy, recurring, source?.toModel(),
    )
}

fun ScheduleResponse.toModel(): Schedule {
    val start = parseServerTime(startsAt)
    val end = parseServerTime(endsAt)
    val (startDay, endDay) = seoulDates(allDay, start, end, startDate, endDate)
    return Schedule(
        id = id,
        groupId = groupId,
        title = title,
        allDay = allDay,
        startsAt = start,
        endsAt = end,
        startDate = startDay,
        endDate = endDay,
        participantIds = participantIds,
        rrule = rrule,
        memo = memo,
        reminders = reminders.mapNotNull(Reminder::of),
        createdBy = createdBy,
        version = version,
        place = place?.toModel(),
        source = source?.toModel(),
    )
}

fun ConflictResponse.toModel() = ScheduleConflict(scheduleId, title, allDay, userIds)

fun HolidayResponse.toModel() = Holiday(LocalDate.parse(date), name, substitute)

/**
 * 일정이 걸친 서울 날짜(마지막 날 포함). 종일은 서버가 준 날짜를 쓴다.
 * 시간 일정이 다음 날 00:00에 끝나면 그날까지만 걸친 것으로 본다 (22:00~24:00은 하루짜리).
 */
private fun seoulDates(
    allDay: Boolean,
    start: Instant,
    end: Instant,
    startDate: String?,
    endDate: String?,
): Pair<LocalDate, LocalDate> {
    if (allDay && startDate != null && endDate != null) return LocalDate.parse(startDate) to LocalDate.parse(endDate)
    val startDay = start.atZone(SeoulZone).toLocalDate()
    val endAt = end.atZone(SeoulZone)
    val lastDay = if (end.isAfter(start) && endAt.toLocalTime() == java.time.LocalTime.MIDNIGHT) {
        endAt.toLocalDate().minusDays(1)
    } else {
        endAt.toLocalDate()
    }
    return startDay to maxOf(startDay, lastDay)
}
