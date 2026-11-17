package com.famlog.testing

import com.famlog.core.datastore.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionStore(userId: Long? = null, currentGroupId: Long? = null) : SessionStore {
    var userId: Long? = userId
    private val current = MutableStateFlow(currentGroupId)
    val currentGroupIdValue: Long? get() = current.value

    override suspend fun userId(): Long? = userId
    override val currentGroupId: Flow<Long?> = current

    override suspend fun saveUser(userId: Long, lastGroupId: Long?) {
        this.userId = userId
        current.value = lastGroupId
    }

    override suspend fun saveCurrentGroup(groupId: Long?) {
        current.value = groupId
    }
}
