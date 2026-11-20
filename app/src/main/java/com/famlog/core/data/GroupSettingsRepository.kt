package com.famlog.core.data

import com.famlog.core.model.AiNotice
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupAiSettings
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.GroupPreferencesRequest
import com.famlog.core.network.GroupSettingsApi
import com.famlog.core.network.UpdateGroupSettingsRequest
import com.famlog.core.network.toModel
import javax.inject.Inject
import javax.inject.Singleton

/** 그룹 AI 분석 설정·내 분석 제외 (SET-01). 서버가 기준이라 기기에 저장하지 않는다 */
@Singleton
class GroupSettingsRepository @Inject constructor(
    private val api: GroupSettingsApi,
    private val caller: ApiCaller,
) {
    /** 안내 시트를 버튼 없이 닫은 그룹. 앱을 켜 둔 동안만 기억한다 (다음에 앱을 열면 서버 기준으로 다시) */
    private val noticeSkipped = mutableSetOf<Long>()

    suspend fun aiNotice(): ApiResult<AiNotice> = caller.call { api.aiNotice().toModel() }

    suspend fun settings(groupId: Long): ApiResult<GroupAiSettings> = caller.call { api.settings(groupId).toModel() }

    /** [noticeVersion]은 관리자에게 보여 준 안내 버전. 켤 때만 필요하다 */
    suspend fun setAiEnabled(groupId: Long, enabled: Boolean, noticeVersion: String?): ApiResult<GroupAiSettings> =
        caller.call { api.update(groupId, UpdateGroupSettingsRequest(enabled, noticeVersion.takeIf { enabled })).toModel() }

    /** 이 그룹에서 내 메시지를 AI 분석에서 뺐는지 */
    suspend fun aiOptOut(groupId: Long): ApiResult<Boolean> = caller.call { api.preferences(groupId) }.map { it.aiOptOut }

    suspend fun setAiOptOut(groupId: Long, optOut: Boolean): ApiResult<Boolean> =
        caller.call { api.updatePreferences(groupId, GroupPreferencesRequest(aiOptOut = optOut)) }.map { it.aiOptOut }

    fun skipNotice(groupId: Long) {
        synchronized(noticeSkipped) { noticeSkipped += groupId }
    }

    fun noticeSkipped(groupId: Long): Boolean = synchronized(noticeSkipped) { groupId in noticeSkipped }
}
