package com.famlog.core.model

import java.time.Instant

/** 메시지 종류 (사양서 10.2 messages.type). 앱이 모르는 값은 [UNKNOWN]으로 받아 "지원하지 않는 메시지"로 보인다 */
enum class MessageType {
    TEXT, IMAGE, VIDEO, VOICE, PLACE, SYSTEM, CANDIDATE, POLL, UNKNOWN;

    companion object {
        fun of(value: String?): MessageType = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** 공감 6종 (CHAT-06). 서버는 이름만 저장하고 그림은 앱이 그린다 */
enum class ReactionEmoji(val glyph: String) {
    HEART("❤️"), LIKE("👍"), LAUGH("😂"), WOW("😮"), SAD("😢"), PRAY("🙏");

    companion object {
        fun of(value: String): ReactionEmoji? = entries.firstOrNull { it.name == value }
    }
}

/**
 * 내가 보낸 메시지의 전송 상태. 서버가 seq를 붙여 돌려주면 [SENT].
 * [SENDING]은 연결되면 같은 clientMessageId로 자동 재전송하고, [FAILED]는 사용자가 [다시 보내기]를 눌러야 한다.
 */
enum class SendState { SENT, SENDING, FAILED }

data class Reaction(val emoji: ReactionEmoji, val userIds: List<Long>)

/** 답장 원문 미리보기. 내가 들어오기 전 메시지면 [body]가 null이다 */
data class ReplyPreview(
    val id: Long,
    val seq: Long,
    val senderId: Long?,
    val type: MessageType,
    val body: String?,
    val deleted: Boolean,
)

/**
 * 채팅 메시지 (CHAT-02~06). 아직 서버에 저장되지 않은 내 메시지는 [id]·[seq]가 null이다.
 * [senderId]가 null이면 시스템 메시지(입장·퇴장)이고 [body]에 서버가 만든 문장이 들어 있다.
 */
data class ChatMessage(
    val key: String,
    val groupId: Long,
    val id: Long?,
    val seq: Long?,
    val senderId: Long?,
    val clientMessageId: String?,
    val type: MessageType,
    val body: String?,
    val replyTo: ReplyPreview?,
    val reactions: List<Reaction>,
    val unreadCount: Int,
    val deleted: Boolean,
    val sentAt: Instant,
    val sendState: SendState,
) {
    val isSystem: Boolean get() = type == MessageType.SYSTEM
}

/** 채팅 탭(SCR-10)에 보일 그룹의 마지막 메시지 */
data class LastMessage(
    val seq: Long,
    val senderId: Long?,
    val type: MessageType,
    val body: String?,
    val deleted: Boolean,
    val sentAt: Instant,
)
