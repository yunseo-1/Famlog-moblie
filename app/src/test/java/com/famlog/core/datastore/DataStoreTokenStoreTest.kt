package com.famlog.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.famlog.core.model.AuthTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreTokenStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    /** 앞에 "enc:"를 붙이는 가짜 암호화. "enc:"가 없으면 복호화 실패로 본다 */
    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(encoded: String) =
            if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
    }

    private val tokens = AuthTokens("access-1", "refresh-1")

    private fun TestScope.newDataStore() = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { tmp.root.resolve("auth.preferences_pb") },
    )

    @Test
    fun `저장한 토큰은 암호화된 값으로 디스크에 남고 새 인스턴스에서도 읽힌다`() = runTest {
        val dataStore = newDataStore()
        DataStoreTokenStore(dataStore, FakeCipher()).save(tokens)

        val raw = dataStore.data.first()
        val rawValues = raw.asMap().values.map { it.toString() }
        assertFalse(rawValues.any { it.contains("access-1") || it.contains("refresh-1") })

        assertEquals(tokens, DataStoreTokenStore(dataStore, FakeCipher()).get())
    }

    @Test
    fun `clear하면 메모리와 디스크 모두 비어 있다`() = runTest {
        val dataStore = newDataStore()
        val store = DataStoreTokenStore(dataStore, FakeCipher())
        store.save(tokens)

        store.clear()

        assertNull(store.get())
        assertNull(DataStoreTokenStore(dataStore, FakeCipher()).get())
    }

    @Test
    fun `복호화에 실패하면 로그아웃 상태로 보고 저장값을 지운다`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit {
            it[stringPreferencesKey("access_token")] = "broken"
            it[stringPreferencesKey("refresh_token")] = "broken"
        }

        assertNull(DataStoreTokenStore(dataStore, FakeCipher()).get())
        assertEquals(emptyMap<Any, Any>(), dataStore.data.first().asMap())
    }
}
