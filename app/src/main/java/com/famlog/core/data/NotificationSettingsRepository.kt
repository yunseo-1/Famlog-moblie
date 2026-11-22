package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.NotificationSettings
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.GroupPreferencesRequest
import com.famlog.core.network.GroupSettingsApi
import com.famlog.core.network.NotificationSettingsApi
import com.famlog.core.network.NotificationSettingsRequest
import com.famlog.core.network.toHourMinute
import com.famlog.core.network.toModel
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** 알림 설정 (SET-02). 서버가 설정대로 푸시를 거르고(그룹 끄기)·본문을 빼고(미리보기)·무음 표시(방해 금지)를 한다 */
@Singleton
class NotificationSettingsRepository @Inject constructor(
    private val api: NotificationSettingsApi,
    private val groupApi: GroupSettingsApi,
    private val caller: ApiCaller,
) {
    suspend fun settings(): ApiResult<NotificationSettings> = caller.call { api.get().toModel() }

    suspend fun setPreviewHidden(hidden: Boolean): ApiResult<NotificationSettings> =
        update(NotificationSettingsRequest(previewHidden = hidden))

    suspend fun setQuietHours(enabled: Boolean): ApiResult<NotificationSettings> =
        update(NotificationSettingsRequest(quietHoursEnabled = enabled))

    /** 시작과 끝이 같으면 서버가 400 */
    suspend fun setQuietTimes(start: LocalTime, end: LocalTime): ApiResult<NotificationSettings> =
        update(NotificationSettingsRequest(quietStart = start.toHourMinute(), quietEnd = end.toHourMinute()))

    /** 이 그룹 새 메시지 알림을 껐는지 */
    suspend fun groupMuted(groupId: Long): ApiResult<Boolean> = caller.call { groupApi.preferences(groupId) }.map { it.notifMuted }

    suspend fun setGroupMuted(groupId: Long, muted: Boolean): ApiResult<Boolean> =
        caller.call { groupApi.updatePreferences(groupId, GroupPreferencesRequest(notifMuted = muted)) }.map { it.notifMuted }

    private suspend fun update(request: NotificationSettingsRequest): ApiResult<NotificationSettings> =
        caller.call { api.update(request).toModel() }
}
