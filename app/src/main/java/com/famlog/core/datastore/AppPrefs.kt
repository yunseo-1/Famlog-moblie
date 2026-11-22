package com.famlog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 계정과 관계없는 이 기기의 앱 설정. 로그아웃해도 남는다 */
interface AppPrefs {
    /** 알림 권한 안내(SCR-04)를 이미 보였는지. 거절해도 다시 묻지 않는다 (설정 화면은 S20) */
    suspend fun notificationPromptShown(): Boolean
    suspend fun setNotificationPromptShown()

    /** 마지막으로 본 공지의 noticeUpdatedAt (SCR-00). 같은 공지는 다시 띄우지 않는다 */
    suspend fun seenNoticeUpdatedAt(): String?
    suspend fun setSeenNoticeUpdatedAt(value: String?)

    /** 앱 잠금(SEC-01). 로그아웃하면 끈다 */
    suspend fun appLockEnabled(): Boolean
    suspend fun setAppLockEnabled(enabled: Boolean)
}

@Singleton
class DataStoreAppPrefs @Inject constructor(
    @AppDataStore private val dataStore: DataStore<Preferences>,
) : AppPrefs {

    override suspend fun notificationPromptShown(): Boolean = dataStore.data.first()[NOTIFICATION_PROMPT_SHOWN] == true

    override suspend fun setNotificationPromptShown() {
        dataStore.edit { it[NOTIFICATION_PROMPT_SHOWN] = true }
    }

    override suspend fun seenNoticeUpdatedAt(): String? = dataStore.data.first()[SEEN_NOTICE_UPDATED_AT]

    override suspend fun setSeenNoticeUpdatedAt(value: String?) {
        dataStore.edit { if (value == null) it.remove(SEEN_NOTICE_UPDATED_AT) else it[SEEN_NOTICE_UPDATED_AT] = value }
    }

    override suspend fun appLockEnabled(): Boolean = dataStore.data.first()[APP_LOCK_ENABLED] == true

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    private companion object {
        val NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
        val SEEN_NOTICE_UPDATED_AT = stringPreferencesKey("seen_notice_updated_at")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }
}
