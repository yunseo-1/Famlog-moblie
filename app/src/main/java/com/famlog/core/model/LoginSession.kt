package com.famlog.core.model

import java.time.Instant

/** 로그인한 기기 하나 (AUTH-03). [current]는 지금 이 기기 */
data class LoginSession(
    val id: Long,
    val deviceName: String?,
    val platform: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val current: Boolean,
)
