package com.famlog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 앱 설치마다 만드는 무작위 기기 ID. 로그인 요청의 device.deviceId로 쓴다 (AUTH-01, AUTH-03) */
@Singleton
class DeviceIdProvider @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
) {
    suspend fun get(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return id
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
