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
)
