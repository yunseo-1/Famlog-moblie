package com.famlog.core.network

import com.famlog.core.model.AiNotice
import com.famlog.core.model.GroupAiSettings
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/** 그룹 AI 분석 설정과 내 분석 제외 (SET-01). 설정 변경은 WebSocket 이벤트가 없어 그룹에 들어갈 때 조회한다 */
interface GroupSettingsApi {
    @GET("ai-notice")
    suspend fun aiNotice(): AiNoticeResponse

    @GET("groups/{groupId}/settings")
    suspend fun settings(@Path("groupId") groupId: Long): GroupSettingsResponse

    /** 관리자 이상. 꺼진 그룹을 켤 때 [UpdateGroupSettingsRequest.aiNoticeVersion] 필수 (없거나 다르면 400, field aiNoticeVersion) */
    @PATCH("groups/{groupId}/settings")
    suspend fun update(@Path("groupId") groupId: Long, @Body body: UpdateGroupSettingsRequest): GroupSettingsResponse

    @GET("groups/{groupId}/members/me/preferences")
    suspend fun preferences(@Path("groupId") groupId: Long): GroupPreferencesResponse

    /** 본인만. 보낸 값만 바꾼다 */
    @PATCH("groups/{groupId}/members/me/preferences")
    suspend fun updatePreferences(@Path("groupId") groupId: Long, @Body body: GroupPreferencesRequest): GroupPreferencesResponse
}

@Serializable
data class AiNoticeResponse(
    val version: String,
    val recipient: String = "",
    val country: String = "",
    val sentItems: List<String> = emptyList(),
    val notSentItems: List<String> = emptyList(),
    val purpose: String = "",
    val retention: String = "",
    val refusal: String = "",
)

@Serializable
data class GroupSettingsResponse(
    val aiAnalysisEnabled: Boolean = false,
    val aiEnabledAt: String? = null,
    val aiNoticeVersion: String? = null,
    val aiNoticePending: Boolean = false,
)

@Serializable
data class UpdateGroupSettingsRequest(val aiAnalysisEnabled: Boolean, val aiNoticeVersion: String? = null)

@Serializable
data class GroupPreferencesResponse(val notifMuted: Boolean = false, val aiOptOut: Boolean = false)

@Serializable
data class GroupPreferencesRequest(val notifMuted: Boolean? = null, val aiOptOut: Boolean? = null)

fun AiNoticeResponse.toModel() = AiNotice(version, recipient, country, sentItems, notSentItems, purpose, retention, refusal)

fun GroupSettingsResponse.toModel() = GroupAiSettings(aiAnalysisEnabled, aiNoticeVersion, aiNoticePending)
