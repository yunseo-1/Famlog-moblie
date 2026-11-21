package com.famlog.core.network

import kotlinx.serialization.Serializable

/** 백엔드 AuthDtos와 같은 모양 (POST /auth/refresh). toString에 토큰을 넣지 않는다 */
@Serializable
data class RefreshRequest(val refreshToken: String) {
    override fun toString(): String = "RefreshRequest(***)"
}

@Serializable
data class TokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
) {
    override fun toString(): String =
        "TokenResponse(accessTokenExpiresAt=$accessTokenExpiresAt, refreshTokenExpiresAt=$refreshTokenExpiresAt)"
}

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val traceId: String? = null,
    val fieldErrors: List<FieldErrorResponse> = emptyList(),
)

@Serializable
data class FieldErrorResponse(val field: String, val reason: String? = null)

@Serializable
data class DeviceRequest(
    val deviceId: String,
    val deviceName: String?,
    val platform: String,
)

@Serializable
data class LoginRequest(
    val providerToken: String,
    val device: DeviceRequest,
    /** 삭제 예정(30일 유예) 계정을 되살리며 로그인한다 */
    val restore: Boolean? = null,
) {
    override fun toString(): String = "LoginRequest(device=$device)"
}

@Serializable
data class DeleteAccountRequest(val deleteMedia: Boolean)

@Serializable
data class AgreementsRequest(
    val termsVersion: String,
    val privacyVersion: String,
)

/** 닉네임·출생연월도 개인정보라 toString에서 뺀다 (사양서 9.8) */
@Serializable
data class SignupRequest(
    val providerToken: String,
    val device: DeviceRequest,
    val nickname: String,
    val birthYm: String,
    val agreements: AgreementsRequest,
) {
    override fun toString(): String = "SignupRequest(device=$device, agreements=$agreements)"
}

/** lastGroupId가 null이면 그룹 시작 화면(SCR-02)으로 간다 (18.1 AUTH-01) */
@Serializable
data class UserResponse(
    val id: Long,
    val nickname: String,
    val lastGroupId: Long? = null,
)

@Serializable
data class AuthResponse(
    val tokens: TokenResponse,
    val user: UserResponse,
)
