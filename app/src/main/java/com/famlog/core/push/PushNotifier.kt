package com.famlog.core.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.famlog.MainActivity
import com.famlog.R
import com.famlog.core.common.RecentIds
import com.famlog.core.model.MessageType
import com.famlog.core.model.PushChannel
import com.famlog.core.model.PushMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 푸시를 알림으로 띄운다 (NOTI-01). 서버는 data 메시지만 보내므로 채널·묶음·문구는 앱이 정한다.
 * - 채팅: 그룹마다 알림 1개(tag `chat:{groupId}`)에 메시지를 쌓고, 그 위에 요약 알림 1개
 * - 방해 금지 시간(`silent`)이면 소리·진동 없이
 * - 미리보기를 숨긴 사용자는 본문 키가 오지 않으므로 "새 메시지"로 보인다
 */
@Singleton
class PushNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** FCM은 같은 메시지를 두 번 보낼 수 있다. 같은 messageId는 한 번만 쌓는다 */
    private val shownChatMessages = RecentIds(CHAT_DEDUPE_CAPACITY)

    /** 앱 시작 때. 이미 있으면 이름만 바뀐다 */
    fun createChannels() {
        val channels = PushChannel.entries.map { channel ->
            NotificationChannelCompat.Builder(channel.id, channel.importance)
                .setName(context.getString(channel.nameRes))
                .build()
        }
        manager.createNotificationChannelsCompat(channels)
    }

    fun show(message: PushMessage) {
        when (message) {
            is PushMessage.Chat -> showChat(message)
            is PushMessage.ScheduleReminder -> showSystem(
                message = message,
                tag = "schedule:${message.scheduleId}",
                title = message.title ?: context.getString(R.string.push_schedule_title_hidden),
                text = context.getString(R.string.push_schedule_text, message.groupName, whenText(message)),
                contentIntent = openSchedule(message.scheduleId),
            )
            is PushMessage.MemberRemoved -> showSystem(
                message = message,
                tag = "removed:${message.groupId}",
                title = message.groupName,
                text = context.getString(R.string.push_member_removed),
            )
            is PushMessage.DataExportReady -> showSystem(
                message = message,
                tag = "export:${message.exportId}",
                title = context.getString(R.string.push_data_export_title),
                text = context.getString(R.string.push_data_export_text),
                contentIntent = openSettings(EXTRA_DATA_EXPORT, REQUEST_DATA_EXPORT),
            )
            is PushMessage.NewDeviceLogin -> showSystem(
                message = message,
                tag = "device:${message.sessionId}",
                title = context.getString(R.string.push_new_device_title),
                text = context.getString(
                    R.string.push_new_device_text,
                    message.deviceName ?: message.platform ?: context.getString(R.string.push_unknown_device),
                ),
                contentIntent = openSettings(EXTRA_SESSIONS, REQUEST_SESSIONS),
            )
        }
    }

    /** 채팅방에 들어가면 그 그룹 알림을 지운다. 남은 채팅 알림이 없으면 요약도 지운다 */
    fun cancelChat(groupId: Long) {
        manager.cancel(chatTag(groupId), ID_CHAT)
        if (activeChatTags().isEmpty()) manager.cancel(TAG_CHAT_SUMMARY, ID_CHAT)
    }

    private fun showChat(message: PushMessage.Chat) {
        if (!shownChatMessages.add(message.messageId)) return
        val tag = chatTag(message.groupId)
        val style = activeNotification(tag)
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.push_me)).build())
        style.conversationTitle = message.groupName
        style.isGroupConversation = true
        val text = chatText(message)
        // 시스템 메시지(입장·퇴장)는 보낸 사람이 없다. 제목에 이미 그룹 이름이 있어 이름 없이 보인다
        val sender = Person.Builder()
            .setName(message.senderName.orEmpty())
            .setKey(message.senderId?.let { "user:$it" })
            .build()
        val sentAt = (message.sentAt ?: Instant.now()).toEpochMilli()
        style.addMessage(text, sentAt, sender)

        val notification = NotificationCompat.Builder(context, message.channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setContentTitle(message.groupName)
            .setContentText(text)
            .setWhen(sentAt)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(GROUP_CHAT)
            .setSilent(message.silent)
            .setContentIntent(openApp(message.groupId))
            .build()
        notify(tag, notification)
        notify(TAG_CHAT_SUMMARY, chatSummary())
    }

    private fun chatText(message: PushMessage.Chat): String = message.body ?: when (message.messageType) {
        MessageType.IMAGE -> context.getString(R.string.push_photos, message.mediaCount.coerceAtLeast(1))
        MessageType.VIDEO -> if (message.mediaCount > 1) {
            context.getString(R.string.push_videos, message.mediaCount)
        } else {
            context.getString(R.string.push_video)
        }
        else -> context.getString(R.string.push_new_message)
    }

    /** 채팅 알림이 여러 개일 때 하나로 접히게 하는 요약. 소리는 각 그룹 알림이 낸다 */
    private fun chatSummary(): Notification = NotificationCompat.Builder(context, PushChannel.CHAT.id)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.push_new_message))
        .setStyle(NotificationCompat.InboxStyle())
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setGroup(GROUP_CHAT)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setContentIntent(openApp(null))
        .build()

    /** "오늘 오후 3:00", "내일 종일", "11월 21일 (토) 오후 3:00" (서울 기준) */
    private fun whenText(message: PushMessage.ScheduleReminder): String {
        val start = message.startsAt?.atZone(SeoulZone) ?: return ""
        val today = LocalDate.now(SeoulZone)
        val day = when (start.toLocalDate()) {
            today -> context.getString(R.string.push_schedule_today)
            today.plusDays(1) -> context.getString(R.string.push_schedule_tomorrow)
            else -> dayFormatter.format(start)
        }
        val time = if (message.allDay) context.getString(R.string.schedule_all_day) else timeFormatter.format(start)
        return "$day $time"
    }

    private fun showSystem(
        message: PushMessage,
        tag: String,
        title: String,
        text: String,
        contentIntent: PendingIntent = openApp(null),
    ) {
        val notification = NotificationCompat.Builder(context, message.channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setSilent(message.silent)
            .setContentIntent(contentIntent)
            .build()
        notify(tag, notification)
    }

    /** 일정 리마인드를 누르면 그 일정 상세로 (MainActivity) */
    private fun openSchedule(scheduleId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_SCHEDULE_BASE + scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 설정 화면 하나를 여는 알림 (내보내기 준비 → 내 데이터 내보내기, 새 기기 로그인 → 로그인 기기). MainActivity가 [extra]를 읽는다 */
    private fun openSettings(extra: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(extra, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 앱을 연다. [groupId]가 있으면 시작 분기를 거친 뒤 그 채팅방으로 간다 (MainActivity) */
    private fun openApp(groupId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (groupId != null) putExtra(EXTRA_GROUP_ID, groupId)
        }
        return PendingIntent.getActivity(
            context,
            groupId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 알림 권한(Android 13+)이 없거나 사용자가 알림을 껐으면 띄우지 않는다 */
    @SuppressLint("MissingPermission")
    private fun notify(tag: String, notification: Notification) {
        if (!canNotify()) return
        manager.notify(tag, ID_CHAT, notification)
    }

    private fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun activeNotification(tag: String): Notification? =
        systemManager().activeNotifications.firstOrNull { it.tag == tag && it.id == ID_CHAT }?.notification

    private fun activeChatTags(): List<String> =
        systemManager().activeNotifications.mapNotNull { it.tag }.filter { it.startsWith(CHAT_TAG_PREFIX) }

    private fun systemManager(): NotificationManager = context.getSystemService(NotificationManager::class.java)

    companion object {
        /** MainActivity가 읽는 알림 탭 대상 그룹 */
        const val EXTRA_GROUP_ID = "com.famlog.extra.GROUP_ID"

        /** MainActivity가 읽는 일정 리마인드 탭 대상 */
        const val EXTRA_SCHEDULE_ID = "com.famlog.extra.SCHEDULE_ID"

        /** MainActivity가 읽는 내보내기 준비 알림 탭 (ACC-02) */
        const val EXTRA_DATA_EXPORT = "com.famlog.extra.DATA_EXPORT"

        /** MainActivity가 읽는 새 기기 로그인 알림 탭 (AUTH-03) */
        const val EXTRA_SESSIONS = "com.famlog.extra.SESSIONS"

        /** 채팅 알림(그룹 ID)과 PendingIntent 요청 코드가 겹치지 않게 */
        private const val REQUEST_SCHEDULE_BASE = 1 shl 30
        private const val REQUEST_DATA_EXPORT = (1 shl 30) - 1
        private const val REQUEST_SESSIONS = (1 shl 30) - 2

        private val SeoulZone: ZoneId = ZoneId.of("Asia/Seoul")
        private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
        private val dayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

        /** 알림은 tag로 구분하고 id는 하나만 쓴다 (그룹 ID가 Int 범위를 넘어도 되게) */
        private const val ID_CHAT = 1
        private const val CHAT_DEDUPE_CAPACITY = 200
        private const val CHAT_TAG_PREFIX = "chat:"
        private const val TAG_CHAT_SUMMARY = "summary:chat"
        private const val GROUP_CHAT = "com.famlog.CHAT"

        private fun chatTag(groupId: Long) = "$CHAT_TAG_PREFIX$groupId"
    }
}

private val PushChannel.id: String
    get() = "famlog.${name.lowercase()}"

private val PushChannel.importance: Int
    get() = when (this) {
        PushChannel.CHAT, PushChannel.SYSTEM -> NotificationManagerCompat.IMPORTANCE_HIGH
        PushChannel.SCHEDULE, PushChannel.AI -> NotificationManagerCompat.IMPORTANCE_DEFAULT
    }

private val PushChannel.nameRes: Int
    get() = when (this) {
        PushChannel.CHAT -> R.string.push_channel_chat
        PushChannel.SCHEDULE -> R.string.push_channel_schedule
        PushChannel.AI -> R.string.push_channel_ai
        PushChannel.SYSTEM -> R.string.push_channel_system
    }
