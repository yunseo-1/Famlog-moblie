package com.famlog.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /** seq 순서, 아직 보내지 못한 메시지는 맨 뒤에 만든 순서로 */
    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY seq IS NULL, seq, localOrder")
    fun observe(groupId: Long): Flow<List<MessageEntity>>

    @Query("SELECT MAX(seq) FROM messages WHERE groupId = :groupId")
    suspend fun maxSeq(groupId: Long): Long?

    @Query("SELECT MIN(seq) FROM messages WHERE groupId = :groupId")
    suspend fun minSeq(groupId: Long): Long?

    @Query("SELECT * FROM messages WHERE `key` = :key")
    suspend fun get(key: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE serverId = :id")
    suspend fun getByServerId(id: Long): MessageEntity?

    @Upsert
    suspend fun upsert(messages: List<MessageEntity>)

    /** 연결되면 다시 보낼 메시지 (만든 순서) */
    @Query("SELECT * FROM messages WHERE sendState = 'SENDING' ORDER BY localOrder")
    suspend fun pending(): List<MessageEntity>

    @Query("UPDATE messages SET sendState = :state WHERE `key` = :key AND seq IS NULL")
    suspend fun updateSendState(key: String, state: String)

    @Query("UPDATE messages SET deleted = 1, body = NULL, reactionsJson = '[]', mediaJson = '[]' WHERE groupId = :groupId AND serverId = :id")
    suspend fun markDeleted(groupId: Long, id: Long)

    @Query("UPDATE messages SET mediaJson = :mediaJson WHERE serverId = :id")
    suspend fun updateMedia(id: Long, mediaJson: String)

    /** 업로드 진행 (파일마다 mediaId를 채운다) */
    @Query("UPDATE messages SET localMediaJson = :localMediaJson WHERE `key` = :key AND seq IS NULL")
    suspend fun updateLocalMedia(key: String, localMediaJson: String)

    @Query("UPDATE messages SET reactionsJson = :reactionsJson WHERE serverId = :id")
    suspend fun updateReactions(id: Long, reactionsJson: String)

    /** read.updated: (fromSeq, toSeq] 중 읽은 사람이 보내지 않은 메시지의 안 읽은 수를 1씩 뺀다 */
    @Query(
        """
        UPDATE messages SET unreadCount = MAX(unreadCount - 1, 0)
        WHERE groupId = :groupId AND seq > :fromSeq AND seq <= :toSeq
          AND (senderId IS NULL OR senderId != :readerId)
        """,
    )
    suspend fun decrementUnread(groupId: Long, fromSeq: Long, toSeq: Long, readerId: Long)

    @Query("DELETE FROM messages WHERE `key` = :key AND seq IS NULL")
    suspend fun deletePending(key: String)

    /** 서버에 저장된 메시지만 지운다 (보내기 전 메시지는 남긴다) */
    @Query("DELETE FROM messages WHERE groupId = :groupId AND seq IS NOT NULL")
    suspend fun clearSent(groupId: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}
