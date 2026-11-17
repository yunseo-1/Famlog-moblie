package com.famlog.feature.chat

import com.famlog.core.model.ChatMessage
import com.famlog.core.model.MessageType
import com.famlog.core.model.SendState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class ChatRowsTest {

    private fun message(seq: Long, sender: Long?, at: String, type: MessageType = MessageType.TEXT) = ChatMessage(
        key = "k$seq", groupId = 1, id = seq, seq = seq, senderId = sender, clientMessageId = null, type = type,
        body = "b$seq", replyTo = null, reactions = emptyList(), unreadCount = 0, deleted = false,
        sentAt = OffsetDateTime.parse(at).toInstant(), sendState = SendState.SENT,
    )

    @Test
    fun 서울_날짜가_바뀌면_날짜_줄을_넣는다() {
        // 11/17 23:59 KST 와 11/18 00:01 KST (UTC로는 같은 날)
        val rows = buildChatRows(
            listOf(message(1, 8, "2026-11-17T23:59:00+09:00"), message(2, 8, "2026-11-18T00:01:00+09:00")),
            SeoulZone,
        )

        assertEquals(
            listOf("date:2026-11-17", "k1", "date:2026-11-18", "k2"),
            rows.map { it.key },
        )
        assertEquals(LocalDate.of(2026, 11, 18), (rows[2] as ChatRow.Date).date)
    }

    @Test
    fun 같은_사람_같은_분이면_이름은_첫_줄에_시각은_마지막_줄에만() {
        val rows = buildChatRows(
            listOf(
                message(1, 8, "2026-11-17T15:12:05+09:00"),
                message(2, 8, "2026-11-17T15:12:40+09:00"),
                message(3, 8, "2026-11-17T15:13:00+09:00"),
                message(4, null, "2026-11-17T15:13:10+09:00", MessageType.SYSTEM),
                message(5, 8, "2026-11-17T15:13:20+09:00"),
            ),
            SeoulZone,
        ).filterIsInstance<ChatRow.Message>()

        assertEquals(listOf(true, false, true, false, true), rows.map { it.showSender })
        assertEquals(listOf(false, true, true, false, true), rows.map { it.showTime })
    }
}
