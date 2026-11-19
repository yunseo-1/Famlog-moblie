package com.famlog.feature.chat

import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateApproval
import com.famlog.core.model.CandidateEvidence
import com.famlog.core.model.CandidateFields
import com.famlog.core.model.Member
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceOption
import com.famlog.core.model.Reminder
import com.famlog.core.model.ScheduleDraft
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

/**
 * 수정 시트(SCR-13) 입력. 유형마다 하나. 서버 승인 요청의 항목 이름(title·date·attendeeIds·assigneeIds·kakaoPlaceId)으로
 * 빈 칸을 알린다.
 */
sealed interface CandidateForm {
    /** 비어 있어 등록할 수 없는 항목 */
    val missing: Set<String>

    /**
     * @param locationText 별칭·원문 장소. [placeId]가 있고 글을 고치지 않았으면 그 장소를 연결하고,
     * 아니면 메모 "장소: …"로 남긴다 (서버 기본값과 같다)
     */
    data class Schedule(
        val title: String,
        val date: LocalDate?,
        val allDay: Boolean,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val participantIds: Set<Long>,
        val locationText: String,
        val placeId: Long?,
        val originalLocation: String,
    ) : CandidateForm {
        override val missing: Set<String>
            get() = buildSet {
                if (title.isBlank()) add(FIELD_TITLE)
                if (date == null) add(FIELD_DATE)
                if (participantIds.isEmpty()) add(FIELD_ATTENDEES)
            }
    }

    /** 매년 반복 종일, 가족 전원. [date]는 다가오는 그 날 */
    data class Anniversary(val title: String, val date: LocalDate?, val personText: String?) : CandidateForm {
        override val missing: Set<String>
            get() = buildSet {
                if (title.isBlank()) add(FIELD_TITLE)
                if (date == null) add(FIELD_DATE)
            }
    }

    data class Todo(val title: String, val assigneeId: Long?, val dueDate: LocalDate?) : CandidateForm {
        override val missing: Set<String> get() = if (title.isBlank()) setOf(FIELD_TITLE) else emptySet()
    }

    /**
     * @param options 서버가 찾은 곳(최대 3) + 다시 검색해 고른 곳
     * @param results 시트 안 검색 결과
     */
    data class Place(
        val options: List<PlaceOption>,
        val selected: PlaceOption?,
        val category: PlaceCategory,
        val review: String,
        val query: String,
        val results: List<PlaceOption> = emptyList(),
        val searching: Boolean = false,
        val searched: Boolean = false,
    ) : CandidateForm {
        override val missing: Set<String> get() = if (selected == null) setOf(FIELD_PLACE) else emptySet()
    }

    companion object {
        const val FIELD_TITLE = "title"
        const val FIELD_DATE = "date"
        const val FIELD_ATTENDEES = "attendeeIds"
        const val FIELD_ASSIGNEES = "assigneeIds"
        const val FIELD_PLACE = "kakaoPlaceId"
        const val FIELD_NAME = "name"
        const val MAX_TITLE = 100
        const val MAX_REVIEW = 100
    }
}

/**
 * 후보 값으로 시트를 채운다. 가족 전체는 지금 가족 모두, 참석자가 없으면 말한 사람(서버 기본값과 같다).
 * 같은 역할이 여러 명이라 골라야 하면 비워 둔다.
 */
fun Candidate.toForm(members: List<Member>, evidence: List<CandidateEvidence>, today: LocalDate): CandidateForm {
    val memberIds = members.map { it.userId }
    val speaker = evidence.lastOrNull()?.senderId?.takeIf { it in memberIds }
    return when (val f = fields) {
        is CandidateFields.Schedule -> {
            val start = f.startTime ?: DEFAULT_START
            val people = when {
                f.allMembers -> memberIds.toSet()
                f.attendeeChoices.isNotEmpty() -> f.attendeeIds.filter { it in memberIds }.toSet()
                else -> f.attendeeIds.filter { it in memberIds }.toSet().ifEmpty { setOfNotNull(speaker) }
            }
            val location = f.location?.text.orEmpty()
            CandidateForm.Schedule(
                title = f.title.orEmpty(),
                date = f.date,
                allDay = f.allDay,
                startTime = start,
                endTime = f.endTime?.takeIf { !it.isBefore(start) } ?: start,
                participantIds = people,
                locationText = location,
                placeId = f.location?.placeId,
                originalLocation = location,
            )
        }
        is CandidateFields.Anniversary -> CandidateForm.Anniversary(
            title = f.title.orEmpty(),
            date = nextDate(f.month, f.day, today),
            personText = f.personText,
        )
        is CandidateFields.Todo -> CandidateForm.Todo(
            title = f.title.orEmpty(),
            assigneeId = f.assigneeIds.singleOrNull()?.takeIf { !f.allMembers && f.assigneeChoices.isEmpty() && it in memberIds },
            dueDate = f.dueDate,
        )
        is CandidateFields.Place -> CandidateForm.Place(
            options = f.options,
            selected = f.chosen,
            category = f.chosen?.category ?: PlaceCategory.ETC,
            review = f.opinion.orEmpty().take(CandidateForm.MAX_REVIEW),
            query = listOfNotNull(f.regionHint, f.name).joinToString(" "),
        )
    }
}

/** 시트 값 → 승인 요청. 빈 칸이 있으면 null */
fun CandidateForm.toApproval(members: List<Member>, zone: ZoneId): CandidateApproval? {
    if (missing.isNotEmpty()) return null
    return when (this) {
        is CandidateForm.Schedule -> {
            val day = date ?: return null
            val keepPlace = placeId != null && locationText.trim() == originalLocation.trim()
            val memo = locationText.trim().takeIf { it.isNotEmpty() && !keepPlace }?.let { "장소: $it" }
            val startsAt = day.atTime(startTime).atZone(zone).toInstant()
            CandidateApproval.Schedule(
                ScheduleDraft(
                    title = title.trim(),
                    allDay = allDay,
                    startsAt = startsAt,
                    endsAt = day.atTime(endTime.takeIf { !it.isBefore(startTime) } ?: startTime).atZone(zone).toInstant(),
                    startDate = day,
                    endDate = day,
                    participantIds = participantIds.toList(),
                    rrule = null,
                    memo = memo,
                    reminders = Reminder.defaults(allDay),
                    placeId = if (keepPlace) placeId else null,
                ),
            )
        }
        is CandidateForm.Anniversary -> {
            val day = date ?: return null
            CandidateApproval.Schedule(
                ScheduleDraft(
                    title = title.trim(),
                    allDay = true,
                    startsAt = null,
                    endsAt = null,
                    startDate = day,
                    endDate = day,
                    participantIds = members.map { it.userId },
                    rrule = YEARLY,
                    memo = personText?.let { "대상: $it" },
                    reminders = Reminder.defaults(allDay = true),
                ),
            )
        }
        is CandidateForm.Todo -> CandidateApproval.Todo(title.trim(), assigneeId, dueDate)
        is CandidateForm.Place -> selected?.let { CandidateApproval.Place(it.kakaoPlaceId, category, review) }
    }
}

/** 다가오는 그 월·일 (오늘이면 오늘). 없는 날(2/30)은 null */
private fun nextDate(month: Int?, day: Int?, today: LocalDate): LocalDate? {
    if (month == null || day == null) return null
    val monthDay = runCatching { MonthDay.of(month, day) }.getOrNull() ?: return null
    if (!monthDay.isValidYear(today.year) && !monthDay.isValidYear(today.year + 1)) return null
    var year = today.year
    while (!monthDay.isValidYear(year) || monthDay.atYear(year).isBefore(today)) year++
    return monthDay.atYear(year)
}

private val DEFAULT_START: LocalTime = LocalTime.of(9, 0)
private const val YEARLY = "FREQ=YEARLY"
