package com.famlog.core.model

/** 서버 공통 에러 응답 { code, message, traceId } (사양서 11.1). message는 사용자에게 그대로 보여줄 수 있다 */
data class ApiError(
    val code: String,
    val message: String,
    val traceId: String?,
)

/** 앱이 분기에 쓰는 에러 코드 (사양서 11.2, 백엔드 ErrorCode) */
object ErrorCodes {
    const val AUTH_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED"
    const val AUTH_INVALID = "AUTH_INVALID"
    const val USER_NOT_REGISTERED = "USER_NOT_REGISTERED"
    const val UPGRADE_REQUIRED = "UPGRADE_REQUIRED"
    const val AGE_RESTRICTED = "AGE_RESTRICTED"
    const val NOT_FOUND = "NOT_FOUND"
    const val INVITE_EXPIRED = "INVITE_EXPIRED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val GROUP_LIMIT_EXCEEDED = "GROUP_LIMIT_EXCEEDED"
}
