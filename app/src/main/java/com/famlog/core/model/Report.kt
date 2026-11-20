package com.famlog.core.model

/** 신고 대상 (MOD-01). MESSAGE: 사람이 보낸 메시지 · MEDIA: 사진·동영상 · AI_OUTPUT: AI 후보 · USER: 구성원(그룹 필요) */
enum class ReportTargetType { MESSAGE, MEDIA, AI_OUTPUT, USER }

/** 신고 사유. OTHER는 내용이 필수, AI_INACCURATE는 AI 결과에만 보인다 */
enum class ReportReason { SPAM, HARASSMENT, SEXUAL, VIOLENCE, CHILD_SAFETY, PRIVACY, AI_INACCURATE, OTHER }

data class ReportTarget(val type: ReportTargetType, val id: Long, val groupId: Long? = null) {
    /** 고를 수 있는 사유 (AI 결과에만 "AI 제안이 틀렸어요") */
    val reasons: List<ReportReason>
        get() = ReportReason.entries.filter { it != ReportReason.AI_INACCURATE || type == ReportTargetType.AI_OUTPUT }
}
