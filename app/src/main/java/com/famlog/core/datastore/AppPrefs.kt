package com.famlog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 계정과 관계없는 이 기기의 앱 설정. 로그아웃해도 남는다 */
interface AppPrefs {
    /** 알림 권한 안내(SCR-04)를 이미 보였는지. 거절해도 다시 묻지 않는다 (설정 화면은 S20) */
    suspend fun notificationPromptShown(): Boolean
    suspend fun setNotificationPromptShown()
}

@Singleton
class DataStoreAppPrefs @Inject constructor(
    @AppDataStore private val dataStore: DataStore<Preferences>,
) : AppPrefs {

    override suspend fun notificationPromptShown(): Boolean = dataStore.data.first()[NOTIFICATION_PROMPT_SHOWN] == true

    override suspend fun setNotificationPromptShown() {
        dataStore.edit { it[NOTIFICATION_PROMPT_SHOWN] = true }
    }

    private companion object {
        val NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
    }
}
