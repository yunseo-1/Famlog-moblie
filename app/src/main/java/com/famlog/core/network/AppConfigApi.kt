package com.famlog.core.network

import com.famlog.core.model.AppConfig
import com.famlog.core.model.AppFeatures
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** 앱 설정 (로그인 없이 호출, X-Platform으로 플랫폼을 고른다). 426을 받지 않는 유일한 API */
interface AppConfigApi {
    @GET("app/config")
    suspend fun config(): AppConfigResponse
}

@Serializable
data class AppConfigResponse(
    val platform: String? = null,
    val minSupportedVersion: String? = null,
    val latestVersion: String? = null,
    val notice: String? = null,
    val noticeUpdatedAt: String? = null,
    val features: AppFeaturesResponse = AppFeaturesResponse(),
)

@Serializable
data class AppFeaturesResponse(val nearby: Boolean = true, val photoLocation: Boolean = false, val aiAnalysis: Boolean = true)

fun AppConfigResponse.toModel() = AppConfig(
    minSupportedVersion = minSupportedVersion,
    latestVersion = latestVersion,
    notice = notice?.takeIf { it.isNotBlank() },
    noticeUpdatedAt = noticeUpdatedAt,
    features = AppFeatures(features.nearby, features.photoLocation, features.aiAnalysis),
)
