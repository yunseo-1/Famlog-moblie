package com.famlog.core.model

import java.time.Instant
import java.time.OffsetDateTime

/** 앱 알림 채널 (NOTI-01). 서버 푸시의 `channel` 값 */
enum class PushChannel {
    CHAT, SCHEDULE, AI, SYSTEM;

    companion object {
        fun of(value: String?): PushChannel = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * 서버 FCM data 메시지 (백엔드 HANDOFF "푸시 규칙"). 값은 모두 문자열로 온다.
 * 알 수 없는 type이거나 필수 키가 없으면 [parse]가 null — 앱이 오래된 버전이어도 깨지지 않게 무시한다.
 */
sealed interface PushMessage {
    val channel: PushChannel

    /** 방해 금지 시간: 소리·진동 없이 띄운다 */
    val silent: Boolean

    /** 새 메시지 (입장·퇴장 시스템 메시지 포함). [body]는 미리보기를 숨겼으면 null */
    data class Chat(
        val groupId: Long,
        val groupName: String,
        val messageId: Long,
        val messageType: MessageType,
        val senderId: Long?,
        val senderName: String?,
        val mediaCount: Int,
        val body: String?,
        val sentAt: Instant?,
        override val channel: PushChannel,
        override val silent: Boolean,
    ) : PushMessage {
        /** 본문이 들어 있어 값은 보이지 않는다 */
        override fun toString(): String = "PushMessage.Chat(groupId=$groupId, messageId=$messageId, type=$messageType)"
    }

    /** 일정 리마인드 (SCH-03). [title]은 미리보기를 숨겼으면 null */
    data class ScheduleReminder(
        val groupId: Long,
        val groupName: String,
        val scheduleId: Long,
        val startsAt: Instant?,
        val allDay: Boolean,
        val title: String?,
        override val channel: PushChannel,
        override val silent: Boolean,
    ) : PushMessage {
        /** 일정 제목이 들어 있어 값은 보이지 않는다 */
        override fun toString(): String = "PushMessage.ScheduleReminder(groupId=$groupId, scheduleId=$scheduleId)"
    }

    /** 그룹에서 내보내진 본인에게 */
    data class MemberRemoved(
        val groupId: Long,
        val groupName: String,
        override val channel: PushChannel,
        override val silent: Boolean,
    ) : PushMessage

    /** 처음 쓰는 기기로 로그인했을 때 그 사람의 다른 기기로 */
    data class NewDeviceLogin(
        val sessionId: Long?,
        val platform: String?,
        val deviceName: String?,
        override val channel: PushChannel,
        override val silent: Boolean,
    ) : PushMessage

    /** 내 데이터 내보내기 ZIP이 준비됐다 (ACC-02). 본문 키는 없다 */
    data class DataExportReady(
        val exportId: Long,
        override val channel: PushChannel,
        override val silent: Boolean,
    ) : PushMessage

    companion object {
        const val TYPE_CHAT_MESSAGE = "CHAT_MESSAGE"
        const val TYPE_MEMBER_REMOVED = "MEMBER_REMOVED"
        const val TYPE_NEW_DEVICE_LOGIN = "NEW_DEVICE_LOGIN"
        const val TYPE_SCHEDULE_REMINDER = "SCHEDULE_REMINDER"
        const val TYPE_DATA_EXPORT_READY = "DATA_EXPORT_READY"

        fun parse(data: Map<String, String>): PushMessage? {
            val channel = PushChannel.of(data["channel"])
            val silent = data["silent"] == "true"
            return when (data["type"]) {
                TYPE_CHAT_MESSAGE -> Chat(
                    groupId = data["groupId"]?.toLongOrNull() ?: return null,
                    groupName = data["groupName"].orEmpty(),
                    messageId = data["messageId"]?.toLongOrNull() ?: return null,
                    messageType = MessageType.of(data["messageType"]),
                    senderId = data["senderId"]?.toLongOrNull(),
                    senderName = data["senderName"]?.takeIf { it.isNotBlank() },
                    mediaCount = data["mediaCount"]?.toIntOrNull() ?: 0,
                    body = data["body"]?.takeIf { it.isNotEmpty() },
                    sentAt = data["sentAt"]?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
                    channel = channel,
                    silent = silent,
                )
                TYPE_SCHEDULE_REMINDER -> ScheduleReminder(
                    groupId = data["groupId"]?.toLongOrNull() ?: return null,
                    groupName = data["groupName"].orEmpty(),
                    scheduleId = data["scheduleId"]?.toLongOrNull() ?: return null,
                    startsAt = data["startsAt"]?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
                    allDay = data["allDay"] == "true",
                    title = data["title"]?.takeIf { it.isNotBlank() },
                    channel = channel,
                    silent = silent,
                )
                TYPE_MEMBER_REMOVED -> MemberRemoved(
                    groupId = data["groupId"]?.toLongOrNull() ?: return null,
                    groupName = data["groupName"].orEmpty(),
                    channel = channel,
                    silent = silent,
                )
                TYPE_NEW_DEVICE_LOGIN -> NewDeviceLogin(
                    sessionId = data["sessionId"]?.toLongOrNull(),
                    platform = data["platform"],
                    deviceName = data["deviceName"]?.takeIf { it.isNotBlank() },
                    channel = channel,
                    silent = silent,
                )
                TYPE_DATA_EXPORT_READY -> DataExportReady(
                    exportId = data["exportId"]?.toLongOrNull() ?: return null,
                    channel = channel,
                    silent = silent,
                )
                else -> null
            }
        }
    }
}
