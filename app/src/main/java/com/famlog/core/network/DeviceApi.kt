package com.famlog.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.PUT

/** 푸시 토큰 (NOTI-01). 기기(세션)당 1개, 로그아웃하면 서버가 지운다 */
interface DeviceApi {
    @PUT("devices/token")
    suspend fun putToken(@Body body: DeviceTokenRequest)
}

@Serializable
data class DeviceTokenRequest(val token: String, val appVersion: String?) {
    /** 토큰은 로그에 남기지 않는다 */
    override fun toString(): String = "DeviceTokenRequest(appVersion=$appVersion)"
}
