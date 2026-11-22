package com.famlog.core.model

/**
 * 서버 공통 에러 응답 { code, message, traceId, fieldErrors } (사양서 11.1). message는 사용자에게 그대로 보여줄 수 있다.
 * [fields]는 입력 오류가 난 항목 이름 (예: 후보 승인 422의 title·date·attendeeIds)
 */
data class ApiError(
    val code: String,
    val message: String,
    val traceId: String?,
    val fields: List<String> = emptyList(),
)

/** 앱이 분기에 쓰는 에러 코드 (사양서 11.2, 백엔드 ErrorCode) */
object ErrorCodes {
    const val AUTH_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED"
    const val AUTH_INVALID = "AUTH_INVALID"
    const val USER_NOT_REGISTERED = "USER_NOT_REGISTERED"

    /** 삭제 예정(30일 유예) 계정으로 로그인했다. message에 복구 기한 */
    const val ACCOUNT_PENDING_DELETION = "ACCOUNT_PENDING_DELETION"
    const val UPGRADE_REQUIRED = "UPGRADE_REQUIRED"
    const val AGE_RESTRICTED = "AGE_RESTRICTED"
    const val NOT_FOUND = "NOT_FOUND"
    const val INVITE_EXPIRED = "INVITE_EXPIRED"
    const val RATE_LIMITED = "RATE_LIMITED"

    /** 보낸 사진·동영상을 붙일 수 없다(정리 배치가 지움·처리 실패 등). 원본으로 다시 올리면 된다 */
    const val MEDIA_UNAVAILABLE = "MEDIA_UNAVAILABLE"
    const val GROUP_LIMIT_EXCEEDED = "GROUP_LIMIT_EXCEEDED"
    const val GROUP_ROLE_REQUIRED = "GROUP_ROLE_REQUIRED"

    /** 다른 구성원이 있는 그룹장은 나가거나 계정을 지우기 전에 그룹장을 넘겨야 한다 */
    const val OWNER_TRANSFER_REQUIRED = "OWNER_TRANSFER_REQUIRED"

    /** 수정하는 사이 다른 사람이 먼저 고쳤다 (일정·할 일 version) */
    const val VERSION_CONFLICT = "VERSION_CONFLICT"

    /** 같은 그룹에 같은 장소가 이미 있다 (PLC-01) */
    const val PLACE_ALREADY_SHARED = "PLACE_ALREADY_SHARED"

    /** 같은 그룹에 같은 별칭이 이미 있다 (PLC-06) */
    const val ALIAS_DUPLICATED = "ALIAS_DUPLICATED"

    /** 위치기반서비스 동의 없이 내 주변을 불렀다 (PLC-04) */
    const val LOCATION_CONSENT_REQUIRED = "LOCATION_CONSENT_REQUIRED"

    /** 후보: 다른 가족이 먼저 처리했다 (message 예: "엄마님이 먼저 등록했어요.") */
    const val CANDIDATE_ALREADY_DECIDED = "CANDIDATE_ALREADY_DECIDED"

    /** 후보: 일정 시각이 지났거나 7일이 지났다 */
    const val CANDIDATE_EXPIRED = "CANDIDATE_EXPIRED"

    /** 후보: 채울 항목이 있다 → 수정 시트 (fieldErrors) */
    const val CANDIDATE_MISSING_FIELDS = "CANDIDATE_MISSING_FIELDS"

    /** 변경·완료 후보: 그사이 대상이 바뀌었거나 지워졌거나 이미 완료됐다. 후보는 PENDING 그대로 */
    const val CANDIDATE_TARGET_CHANGED = "CANDIDATE_TARGET_CHANGED"

    /** 후보: 승인한 사람·관리자가 아니거나 5분이 지났다 */
    const val CANDIDATE_NOT_UNDOABLE = "CANDIDATE_NOT_UNDOABLE"
}
