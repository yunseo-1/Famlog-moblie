package com.famlog.core.model

/** 서버가 발급한 토큰 쌍. toString에 토큰 값을 넣지 않는다 (사양서 9.8) */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "AuthTokens(***)"
}
