package com.famlog.core.network

import com.famlog.core.model.LastMessage
import com.famlog.core.model.MessageType
import com.famlog.core.model.Reaction
import com.famlog.core.model.ReactionEmoji
import com.famlog.core.model.ReplyPreview
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * 백엔드 ChatDtos와 같은 모양 (사양서 11.3 CHAT-03·04·06, 11.4). 종류·공감 값은 모르는 값이 와도 깨지지 않게 문자열로 받는다.
 * 시각은 "+09:00" ISO-8601 문자열이다.
 */
@Serializable
data class SendMessageRequest(
    val clientMessageId: String,
    val type: String = "TEXT",
    val body: String? = null,
    val mediaIds: List<Long>? = null,
    val replyToId: Long? = null,
)

@Serializable
data class ReadRequest(val lastReadSeq: Long)

@Serializable
data class ReactionRequest(val emoji: String)

@Serializable
data class ReplyPreviewResponse(
    val id: Long,
    val seq: Long,
    val senderId: Long? = null,
    val type: String,
    val body: String? = null,
    val deleted: Boolean = false,
)

@Serializable
data class ReactionResponse(val emoji: String, val userIds: List<Long>)

@Serializable
data class MessageResponse(
    val id: Long,
    val groupId: Long,
    val seq: Long,
    val senderId: Long? = null,
    val clientMessageId: String? = null,
    val type: String,
    val body: String? = null,
    val payload: JsonObject? = null,
    val replyTo: ReplyPreviewResponse? = null,
    val reactions: List<ReactionResponse> = emptyList(),
    val media: List<MediaResponse> = emptyList(),
    val unreadCount: Int = 0,
    val deleted: Boolean = false,
    val sentAt: String,
)

@Serializable
data class MessagePage(val items: List<MessageResponse>, val hasMore: Boolean)

@Serializable
data class ReadResponse(val lastReadSeq: Long)

@Serializable
data class ReactionsResponse(
    val groupId: Long,
    val messageId: Long,
    val seq: Long,
    val reactions: List<ReactionResponse>,
)

@Serializable
data class LastMessageResponse(
    val seq: Long,
    val senderId: Long? = null,
    val type: String,
    val body: String? = null,
    val deleted: Boolean = false,
    val sentAt: String,
)

// --- 실시간 이벤트 (/sub/groups/{groupId}, /user/queue/events) ---

/** 모든 실시간 이벤트는 {type, data}로 온다. data는 type에 따라 아래 클래스로 읽는다 */
@Serializable
data class ChatEventEnvelope(val type: String, val data: JsonElement? = null)

@Serializable
data class ReadUpdated(val groupId: Long, val userId: Long, val fromSeq: Long, val lastReadSeq: Long)

@Serializable
data class MessageDeleted(val groupId: Long, val id: Long, val seq: Long)

@Serializable
data class ChatErrorEvent(val code: String, val message: String? = null, val clientMessageId: String? = null)

object ChatEventTypes {
    const val MESSAGE_CREATED = "message.created"
    const val MESSAGE_DELETED = "message.deleted"
    const val READ_UPDATED = "read.updated"
    const val REACTION_UPDATED = "reaction.updated"
    const val MEDIA_UPDATED = "media.updated"
    const val ERROR = "error"
    const val SESSION_REVOKED = "session.revoked"
}

fun parseServerTime(value: String): Instant = OffsetDateTime.parse(value).toInstant()

fun ReactionResponse.toModel(): Reaction? = ReactionEmoji.of(emoji)?.let { Reaction(it, userIds) }

fun List<ReactionResponse>.toReactions(): List<Reaction> = mapNotNull { it.toModel() }

fun ReplyPreviewResponse.toModel() = ReplyPreview(id, seq, senderId, MessageType.of(type), body, deleted)

fun LastMessageResponse.toModel() =
    LastMessage(seq, senderId, MessageType.of(type), body, deleted, parseServerTime(sentAt))
