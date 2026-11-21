package com.famlog.core.data

import com.famlog.core.datastore.AppPrefs
import com.famlog.core.model.ApiResult
import com.famlog.core.model.AppConfig
import com.famlog.core.model.AppFeatures
import com.famlog.core.model.AppVersions
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.AppConfigApi
import com.famlog.core.network.AppVersion
import com.famlog.core.network.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 앱 시작 때 띄울 안내. 공지가 먼저, 공지가 없으면 선택 업데이트 */
sealed interface StartPrompt {
    data class Notice(val text: String, val updatedAt: String?) : StartPrompt
    data object OptionalUpdate : StartPrompt
}

/** 앱 설정 (SCR-00): 강제·선택 업데이트, 공지, 기능 스위치 */
@Singleton
class AppConfigRepository @Inject constructor(
    private val api: AppConfigApi,
    private val caller: ApiCaller,
    private val appPrefs: AppPrefs,
    @AppVersion private val appVersion: String,
) {
    private val _config = MutableStateFlow<AppConfig?>(null)
    val config: StateFlow<AppConfig?> = _config.asStateFlow()

    /** 받기 전·실패하면 기본값(모두 켜짐, 사진 위치 꺼짐) */
    val features: Flow<AppFeatures> = _config.map { it?.features ?: AppFeatures() }

    /** 앱을 켜 둔 동안 선택 업데이트를 이미 물었다 */
    private var updateAsked = false

    suspend fun refresh(): ApiResult<AppConfig> =
        caller.call { api.config().toModel() }.also { if (it is ApiResult.Success) _config.value = it.value }

    /** 내 버전이 최소 지원 버전보다 낮다 */
    fun updateRequired(): Boolean {
        val config = _config.value ?: return false
        return AppVersions.isLower(appVersion, config.minSupportedVersion)
    }

    /** 시작 때 한 번 보일 안내. 같은 공지([AppConfig.noticeUpdatedAt])는 다시 띄우지 않는다 */
    suspend fun nextPrompt(): StartPrompt? {
        val config = _config.value ?: return null
        val notice = config.notice
        if (notice != null && config.noticeUpdatedAt != appPrefs.seenNoticeUpdatedAt()) {
            return StartPrompt.Notice(notice, config.noticeUpdatedAt)
        }
        if (!updateAsked && AppVersions.isLower(appVersion, config.latestVersion)) {
            updateAsked = true
            return StartPrompt.OptionalUpdate
        }
        return null
    }

    suspend fun markNoticeSeen(updatedAt: String?) = appPrefs.setSeenNoticeUpdatedAt(updatedAt)
}
