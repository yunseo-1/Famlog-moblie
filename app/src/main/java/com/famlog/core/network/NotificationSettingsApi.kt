package com.famlog.core.network

import com.famlog.core.model.NotificationSettings
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 알림 설정 (SET-02): 미리보기 숨기기·방해 금지 시간. 그룹 알림 끄기는 [GroupSettingsApi.updatePreferences] */
interface NotificationSettingsApi {
    @GET("users/me/notification-settings")
    suspend fun get(): NotificationSettingsResponse

    /** 보낸 값만 바꾼다. 시작과 끝 시각이 같으면 400 */
    @PATCH("users/me/notification-settings")
    suspend fun update(@Body body: NotificationSettingsRequest): NotificationSettingsResponse
}

@Serializable
data class NotificationSettingsResponse(
    val previewHidden: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
)

@Serializable
data class NotificationSettingsRequest(
    val previewHidden: Boolean? = null,
    val quietHoursEnabled: Boolean? = null,
    val quietStart: String? = null,
    val quietEnd: String? = null,
)

private val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalTime.toHourMinute(): String = format(HourMinute)

fun NotificationSettingsResponse.toModel() = NotificationSettings(
    previewHidden = previewHidden,
    quietHoursEnabled = quietHoursEnabled,
    quietStart = LocalTime.parse(quietStart, HourMinute),
    quietEnd = LocalTime.parse(quietEnd, HourMinute),
)
