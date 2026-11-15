package com.famlog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.famlog.core.model.AuthTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface TokenStore {
    suspend fun get(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}

/**
 * 토큰을 암호화해 DataStore에 저장한다. OkHttp 스레드에서 자주 읽으므로 첫 읽기 이후에는 메모리 값을 쓴다.
 * 복호화에 실패하면(Keystore 키 분실 등) 저장값을 지우고 로그아웃 상태로 본다.
 */
@Singleton
class DataStoreTokenStore @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : TokenStore {

    private val mutex = Mutex()
    private var loaded = false
    private var cached: AuthTokens? = null

    override suspend fun get(): AuthTokens? = mutex.withLock {
        if (!loaded) {
            cached = readFromDisk()
            loaded = true
        }
        cached
    }

    override suspend fun save(tokens: AuthTokens) = mutex.withLock {
        dataStore.edit {
            it[ACCESS_TOKEN] = cipher.encrypt(tokens.accessToken)
            it[REFRESH_TOKEN] = cipher.encrypt(tokens.refreshToken)
        }
        cached = tokens
        loaded = true
    }

    override suspend fun clear() = mutex.withLock {
        clearDisk()
        cached = null
        loaded = true
    }

    private suspend fun readFromDisk(): AuthTokens? {
        val prefs = dataStore.data.first()
        val access = prefs[ACCESS_TOKEN]?.let(cipher::decrypt)
        val refresh = prefs[REFRESH_TOKEN]?.let(cipher::decrypt)
        if (access == null || refresh == null) {
            if (prefs[ACCESS_TOKEN] != null || prefs[REFRESH_TOKEN] != null) clearDisk()
            return null
        }
        return AuthTokens(access, refresh)
    }

    private suspend fun clearDisk() {
        dataStore.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(REFRESH_TOKEN)
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
