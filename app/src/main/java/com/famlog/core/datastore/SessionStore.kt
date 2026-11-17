package com.famlog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로그인한 사용자 ID와 현재 가족 그룹 ID. GET /users/me가 없어서 로그인 응답의 값을 저장해 둔다.
 * 현재 그룹은 앱을 다시 켜도 유지돼야 한다 (18.1 GRP-03).
 */
interface SessionStore {
    suspend fun userId(): Long?
    val currentGroupId: Flow<Long?>

    /** 로그인·가입 직후. 다른 계정으로 바꿔 들어와도 이전 계정의 현재 그룹이 남지 않게 함께 덮어쓴다 */
    suspend fun saveUser(userId: Long, lastGroupId: Long?)
    suspend fun saveCurrentGroup(groupId: Long?)
}

@Singleton
class DataStoreSessionStore @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
) : SessionStore {

    override suspend fun userId(): Long? = dataStore.data.first()[USER_ID]

    override val currentGroupId: Flow<Long?> = dataStore.data.map { it[CURRENT_GROUP_ID] }

    override suspend fun saveUser(userId: Long, lastGroupId: Long?) {
        dataStore.edit {
            it[USER_ID] = userId
            if (lastGroupId != null) it[CURRENT_GROUP_ID] = lastGroupId else it.remove(CURRENT_GROUP_ID)
        }
    }

    override suspend fun saveCurrentGroup(groupId: Long?) {
        dataStore.edit {
            if (groupId != null) it[CURRENT_GROUP_ID] = groupId else it.remove(CURRENT_GROUP_ID)
        }
    }

    private companion object {
        val USER_ID = longPreferencesKey("user_id")
        val CURRENT_GROUP_ID = longPreferencesKey("current_group_id")
    }
}
