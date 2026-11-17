package com.famlog.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 채팅 메시지 캐시 (CHAT-03). 서버에 저장되기 전 내 메시지도 같은 표에 넣어 바로 화면에 보이고,
 * 서버가 같은 clientMessageId로 돌려주면 그 줄을 덮어쓴다. 그래서 키는 clientMessageId(없으면 "id:{서버 id}")다.
 *
 * @param seq 서버 순번. 아직 보내지 못한 메시지는 null
 * @param replyJson 답장 원문 미리보기 (ReplyPreviewResponse JSON)
 * @param reactionsJson 공감 목록 (ReactionResponse 배열 JSON)
 * @param localOrder 보내기 전 메시지의 순서 (만든 시각, 밀리초)
 */
@Entity(
    tableName = "messages",
    indices = [Index("groupId", "seq"), Index("serverId"), Index("sendState")],
)
data class MessageEntity(
    @PrimaryKey val key: String,
    val groupId: Long,
    val serverId: Long?,
    val seq: Long?,
    val senderId: Long?,
    val clientMessageId: String?,
    val type: String,
    val body: String?,
    val replyToId: Long?,
    val replyJson: String?,
    val reactionsJson: String,
    val unreadCount: Int,
    val deleted: Boolean,
    val sentAtMillis: Long,
    val sendState: String,
    val localOrder: Long,
)
