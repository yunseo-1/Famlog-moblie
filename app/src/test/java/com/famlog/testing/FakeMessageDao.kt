package com.famlog.testing

import com.famlog.core.database.MessageDao
import com.famlog.core.database.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Room [MessageDao]와 같은 동작을 메모리로 흉내 낸다. SQL 자체는 androidTest의 MessageDaoTest가 확인한다 */
class FakeMessageDao : MessageDao {
    private val rows = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())
    var clearCount = 0
        private set

    val all: List<MessageEntity> get() = rows.value.values.toList()

    override fun observe(groupId: Long): Flow<List<MessageEntity>> = rows.map { map ->
        map.values.filter { it.groupId == groupId }
            .sortedWith(compareBy<MessageEntity> { it.seq == null }.thenBy { it.seq }.thenBy { it.localOrder })
    }

    override suspend fun maxSeq(groupId: Long): Long? = inGroup(groupId).mapNotNull { it.seq }.maxOrNull()
    override suspend fun minSeq(groupId: Long): Long? = inGroup(groupId).mapNotNull { it.seq }.minOrNull()
    override suspend fun get(key: String): MessageEntity? = rows.value[key]
    override suspend fun getByServerId(id: Long): MessageEntity? = rows.value.values.firstOrNull { it.serverId == id }

    override suspend fun updateMedia(id: Long, mediaJson: String) =
        edit({ it.serverId == id }) { it.copy(mediaJson = mediaJson) }

    override suspend fun updateLocalMedia(key: String, localMediaJson: String) =
        edit({ it.key == key && it.seq == null }) { it.copy(localMediaJson = localMediaJson) }

    override suspend fun upsert(messages: List<MessageEntity>) {
        rows.value = rows.value + messages.associateBy { it.key }
    }

    override suspend fun pending(): List<MessageEntity> =
        rows.value.values.filter { it.sendState == "SENDING" }.sortedBy { it.localOrder }

    override suspend fun updateSendState(key: String, state: String) =
        edit({ it.key == key && it.seq == null }) { it.copy(sendState = state) }

    override suspend fun markDeleted(groupId: Long, id: Long) =
        edit({ it.groupId == groupId && it.serverId == id }) {
            it.copy(deleted = true, body = null, reactionsJson = "[]", mediaJson = "[]")
        }

    override suspend fun updateReactions(id: Long, reactionsJson: String) =
        edit({ it.serverId == id }) { it.copy(reactionsJson = reactionsJson) }

    override suspend fun decrementUnread(groupId: Long, fromSeq: Long, toSeq: Long, readerId: Long) =
        edit({
            it.groupId == groupId && it.seq != null && it.seq!! > fromSeq && it.seq!! <= toSeq &&
                (it.senderId == null || it.senderId != readerId)
        }) { it.copy(unreadCount = maxOf(it.unreadCount - 1, 0)) }

    override suspend fun deletePending(key: String) {
        rows.value = rows.value.filterNot { (k, v) -> k == key && v.seq == null }
    }

    override suspend fun clearSent(groupId: Long) {
        rows.value = rows.value.filterNot { (_, v) -> v.groupId == groupId && v.seq != null }
    }

    override suspend fun clear() {
        clearCount++
        rows.value = emptyMap()
    }

    private fun inGroup(groupId: Long) = rows.value.values.filter { it.groupId == groupId }

    private fun edit(where: (MessageEntity) -> Boolean, change: (MessageEntity) -> MessageEntity) {
        rows.value = rows.value.mapValues { (_, v) -> if (where(v)) change(v) else v }
    }
}
