package com.famlog.core.network

import com.famlog.core.model.LoginSession
import kotlinx.serialization.Serializable
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

/** 로그인 기기 관리 (AUTH-03) */
interface SessionApi {
    /** 최근 사용 순. current=true가 지금 기기 */
    @GET("users/me/sessions")
    suspend fun list(): List<SessionResponse>

    /** 그 기기의 다음 API 호출부터 401. 내 세션이 아니면 404 */
    @DELETE("users/me/sessions/{sessionId}")
    suspend fun revoke(@Path("sessionId") sessionId: Long)
}

@Serializable
data class SessionResponse(
    val sessionId: Long,
    val deviceName: String? = null,
    val platform: String? = null,
    val createdAt: String,
    val lastUsedAt: String? = null,
    val current: Boolean = false,
)

fun SessionResponse.toModel() = LoginSession(
    id = sessionId,
    deviceName = deviceName?.takeIf { it.isNotBlank() },
    platform = platform,
    createdAt = parseServerTime(createdAt),
    lastUsedAt = lastUsedAt?.let(::parseServerTime),
    current = current,
)
