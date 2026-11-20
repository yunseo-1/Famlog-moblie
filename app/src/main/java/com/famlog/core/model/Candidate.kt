package com.famlog.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 등록 후보 유형 (사양서 6.1). 카드·칩·캘린더·지도 핀의 유형 색도 이 값으로 고른다 */
enum class CandidateType { SCHEDULE, ANNIVERSARY, TODO, PLACE }

enum class CandidateStatus {
    PENDING, APPROVED, DISMISSED, EXPIRED, UNDONE;

    companion object {
        fun of(value: String?): CandidateStatus = entries.firstOrNull { it.name == value } ?: PENDING
    }
}

/** CARD: 신뢰도 0.75 이상, 채팅방 카드 / INBOX: 제안함(SCR-14)에만 */
enum class CandidateSurface { CARD, INBOX }

/** [무시] 사유 (선택) */
enum class DismissReason { ALREADY_REGISTERED, JOKE, WRONG }

/** 승인으로 만든 항목 종류. 기념일도 일정이다 */
enum class CandidateEntity { SCHEDULE, TODO, PLACE }

/**
 * AI가 만든 등록 후보 (ARC-05). 승인 전에는 캘린더·할 일·장소에 없다.
 * 카드는 [anchorMessageId](마지막 근거 메시지) 아래에 붙는다.
 */
data class Candidate(
    val id: Long,
    val groupId: Long,
    val type: CandidateType,
    val status: CandidateStatus,
    val fields: CandidateFields,
    /** "추정" 배지를 붙일 항목: date, startTime.ampm, attendeeIds, assigneeIds, dueDate */
    val inferred: Set<String>,
    /** 채워야 등록되는 항목: title, date, name */
    val missingFields: Set<String>,
    val confidence: Double,
    val surface: CandidateSurface,
    val anchorMessageId: Long?,
    val anchorSeq: Long?,
    val version: Long,
    val decidedBy: Long? = null,
    val decidedAt: Instant? = null,
    val entityType: CandidateEntity? = null,
    val entityId: Long? = null,
    val linkedExisting: Boolean = false,
    val expiresAt: Instant? = null,
    val createdAt: Instant,
    /** 기존 일정 변경·할 일 완료 후보의 대상. 새 항목 후보는 null */
    val target: CandidateTarget? = null,
) {
    fun isInferred(key: String): Boolean = key in inferred

    companion object {
        const val INFERRED_DATE = "date"
        const val INFERRED_AMPM = "startTime.ampm"
        const val INFERRED_ATTENDEES = "attendeeIds"
        const val INFERRED_ASSIGNEES = "assigneeIds"
        const val INFERRED_DUE = "dueDate"
    }
}

/** 서버가 정규화한 값. 유형마다 형태가 다르다 (백엔드 HANDOFF "후보 생성 규칙") */
sealed interface CandidateFields {
    val title: String?

    /**
     * @param allMembers "가족 전체"(추정)
     * @param attendeeChoices 같은 역할이 여러 명이라 골라야 하는 묶음
     * @param unmatchedAttendees 구성원으로 못 찾은 표현 (글자로 보여 준다)
     */
    data class Schedule(
        override val title: String?,
        val date: LocalDate?,
        val startTime: LocalTime?,
        val endTime: LocalTime?,
        val location: CandidateLocation?,
        val attendeeIds: List<Long>,
        val allMembers: Boolean,
        val attendeeChoices: List<List<Long>>,
        val unmatchedAttendees: List<String>,
    ) : CandidateFields {
        val allDay: Boolean get() = startTime == null
    }

    /** 매년 반복 종일 일정이 된다. 음력·미리 알림은 v1.1 */
    data class Anniversary(
        override val title: String?,
        val month: Int?,
        val day: Int?,
        val personText: String?,
    ) : CandidateFields

    data class Todo(
        override val title: String?,
        val dueDate: LocalDate?,
        val assigneeIds: List<Long>,
        val allMembers: Boolean,
        val assigneeChoices: List<List<Long>>,
        val unmatchedAssignees: List<String>,
    ) : CandidateFields

    /** [kakaoPlaceId]는 [options]가 하나면 자동으로 골라져 있다. [recommendedBy]는 근거 메시지를 보낸 사람 */
    data class Place(
        val name: String?,
        val regionHint: String?,
        val kakaoPlaceId: String?,
        val options: List<PlaceOption>,
        val opinion: String?,
        val recommendedBy: Long?,
    ) : CandidateFields {
        override val title: String? get() = name
        val chosen: PlaceOption? get() = options.firstOrNull { it.kakaoPlaceId == kakaoPlaceId }
    }
}

/** 일정 장소. 별칭(PLC-06)과 맞으면 [aliasId], 그 별칭에 연결한 장소가 있으면 [placeId] */
data class CandidateLocation(val text: String?, val aliasId: Long?, val placeId: Long?)

/** 장소 후보의 카카오 검색 결과 (최대 3개) */
data class PlaceOption(
    val kakaoPlaceId: String,
    val name: String,
    val category: PlaceCategory,
    val categoryDetail: String?,
    val address: String?,
    val region1: String?,
    val region2: String?,
)

/** 근거 메시지. 지운 메시지는 [text]가 null. [spans]는 원문에서 강조할 부분 */
data class CandidateEvidence(
    val messageId: Long,
    val seq: Long,
    val senderId: Long?,
    val sentAt: Instant,
    val spans: List<String>,
    val text: String?,
)

data class CandidateDetail(val candidate: Candidate, val evidence: List<CandidateEvidence>)

data class CandidatePage(val items: List<Candidate>, val nextCursor: String?)

/** 다른 가족이 처리했다 (candidate.resolved) */
data class CandidateResolved(
    val groupId: Long,
    val candidateId: Long,
    val status: CandidateStatus,
    val decidedBy: Long?,
    val decidedByName: String?,
    val entityType: CandidateEntity?,
    val entityId: Long?,
    val linkedExisting: Boolean,
)

/**
 * 채팅에서 등록한 항목의 원문 (ARC-06). [candidateId]가 있으면 그 후보의 근거·추출 부분으로 강조하고,
 * 없으면 [messageId] 메시지 전체를 강조한다.
 */
data class ItemSource(val messageId: Long, val seq: Long, val candidateId: Long? = null)

enum class TargetAction { CHANGE, COMPLETE }

/**
 * 기존 항목 변경·완료 후보의 대상 ("11시 말고 12시로 하자" → 그 일정, "세제 샀어" → 그 할 일).
 * 서버가 그룹의 기존 항목에서 찾는다. 여럿(최대 5)이면 [item]이 null이고 [choices]에서 고른다.
 */
data class CandidateTarget(
    val entityType: CandidateEntity,
    val action: TargetAction,
    val item: TargetItem?,
    val choices: List<TargetItem>,
) {
    /** 대상이 정해졌으면 그것, 아니면 고른 것 */
    fun resolve(pickedId: Long?): TargetItem? = item ?: choices.firstOrNull { it.entityId == pickedId }

    /** 일정 변경은 등록한 사람·관리자 이상만 (서버도 403으로 막는다). 할 일 완료는 구성원 누구나 */
    fun allows(item: TargetItem, me: Long?, role: GroupRole): Boolean =
        action == TargetAction.COMPLETE || role != GroupRole.MEMBER || item.createdBy == null || item.createdBy == me
}

/**
 * 대상 항목. 일정은 [before]·[after](바꾸기 전·후 시각), 할 일은 [dueDate]·[assigneeId](지금 값)
 */
data class TargetItem(
    val entityId: Long,
    val title: String?,
    val createdBy: Long? = null,
    val before: ScheduleTiming? = null,
    val after: ScheduleTiming? = null,
    val dueDate: LocalDate? = null,
    val assigneeId: Long? = null,
)

/** 일정 날짜·시각 (서울). 종일이면 시각이 null */
data class ScheduleTiming(val date: LocalDate, val startTime: LocalTime?, val endTime: LocalTime?, val allDay: Boolean)

/** 수정 시트(SCR-13)에서 고친 값. 유형에 맞는 것 하나만 보낸다 */
sealed interface CandidateApproval {
    data class Schedule(val draft: ScheduleDraft) : CandidateApproval
    data class Todo(val title: String, val assigneeId: Long?, val dueDate: LocalDate?) : CandidateApproval
    data class Place(val kakaoPlaceId: String, val category: PlaceCategory, val oneLineReview: String?) : CandidateApproval

    /** 변경·완료 후보: 값은 보내지 않고, 대상이 여럿이면 고른 것 */
    data class Target(val targetId: Long?) : CandidateApproval
}
