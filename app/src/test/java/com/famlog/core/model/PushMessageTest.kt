package com.famlog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PushMessageTest {

    private val chat = mapOf(
        "type" to "CHAT_MESSAGE",
        "channel" to "CHAT",
        "silent" to "false",
        "sentAt" to "2026-11-18T21:05:00+09:00",
        "groupId" to "7",
        "groupName" to "우리 가족",
        "messageId" to "120",
        "seq" to "45",
        "messageType" to "TEXT",
        "senderId" to "3",
        "senderName" to "엄마",
        "body" to "저녁 먹었어?",
    )

    @Test
    fun 채팅_메시지를_읽는다() {
        val message = PushMessage.parse(chat) as PushMessage.Chat

        assertEquals(7L, message.groupId)
        assertEquals("우리 가족", message.groupName)
        assertEquals(120L, message.messageId)
        assertEquals(MessageType.TEXT, message.messageType)
        assertEquals(3L, message.senderId)
        assertEquals("엄마", message.senderName)
        assertEquals("저녁 먹었어?", message.body)
        assertEquals(Instant.parse("2026-11-18T12:05:00Z"), message.sentAt)
        assertEquals(PushChannel.CHAT, message.channel)
        assertFalse(message.silent)
    }

    @Test
    fun 미리보기를_숨기면_본문이_없다() {
        val message = PushMessage.parse(chat - "body") as PushMessage.Chat

        assertNull(message.body)
    }

    @Test
    fun 방해_금지_시간이면_무음이다() {
        val message = PushMessage.parse(chat + ("silent" to "true"))!!

        assertTrue(message.silent)
    }

    @Test
    fun 사진_메시지는_장수를_읽는다() {
        val data = chat - "body" + mapOf("messageType" to "IMAGE", "mediaCount" to "3")

        val message = PushMessage.parse(data) as PushMessage.Chat

        assertEquals(MessageType.IMAGE, message.messageType)
        assertEquals(3, message.mediaCount)
    }

    @Test
    fun 시스템_메시지는_보낸_사람이_없다() {
        val data = chat - "senderId" - "senderName" + ("messageType" to "SYSTEM")

        val message = PushMessage.parse(data) as PushMessage.Chat

        assertNull(message.senderId)
        assertNull(message.senderName)
        assertEquals(MessageType.SYSTEM, message.messageType)
    }

    @Test
    fun 필수_키가_없거나_모르는_종류면_무시한다() {
        assertNull(PushMessage.parse(chat - "groupId"))
        assertNull(PushMessage.parse(chat + ("messageId" to "abc")))
        assertNull(PushMessage.parse(chat + ("type" to "SCHEDULE_REMINDER")))
        assertNull(PushMessage.parse(emptyMap()))
    }

    @Test
    fun 내보내짐과_새_기기_로그인을_읽는다() {
        val removed = PushMessage.parse(
            mapOf("type" to "MEMBER_REMOVED", "channel" to "SYSTEM", "silent" to "false", "groupId" to "7", "groupName" to "우리 가족"),
        )
        val device = PushMessage.parse(
            mapOf("type" to "NEW_DEVICE_LOGIN", "channel" to "SYSTEM", "silent" to "true", "sessionId" to "9", "platform" to "ANDROID"),
        )

        assertEquals(PushMessage.MemberRemoved(7, "우리 가족", PushChannel.SYSTEM, false), removed)
        assertEquals(PushMessage.NewDeviceLogin(9, "ANDROID", null, PushChannel.SYSTEM, true), device)
    }

    @Test
    fun 본문은_문자열로_보이지_않는다() {
        val message = PushMessage.parse(chat)!!

        assertFalse(message.toString().contains("저녁"))
    }

    @Test
    fun 일정_리마인드를_읽고_미리보기를_숨기면_제목이_없다() {
        val data = mapOf(
            "type" to "SCHEDULE_REMINDER", "channel" to "SCHEDULE", "silent" to "false",
            "groupId" to "7", "groupName" to "우리 가족", "scheduleId" to "31",
            "startsAt" to "2026-11-21T18:00:00+09:00", "allDay" to "false", "reminder" to "HOUR_1",
            "title" to "할머니 생신 저녁",
        )

        val reminder = PushMessage.parse(data) as PushMessage.ScheduleReminder
        assertEquals(31L, reminder.scheduleId)
        assertEquals(Instant.parse("2026-11-21T09:00:00Z"), reminder.startsAt)
        assertEquals(PushChannel.SCHEDULE, reminder.channel)
        assertEquals("할머니 생신 저녁", reminder.title)
        assertFalse(reminder.toString().contains("할머니"))

        assertNull((PushMessage.parse(data - "title") as PushMessage.ScheduleReminder).title)
        assertNull(PushMessage.parse(data - "scheduleId"))
    }
}
