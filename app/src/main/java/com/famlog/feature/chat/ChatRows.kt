package com.famlog.feature.chat

import com.famlog.core.model.ChatMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 채팅방 목록 한 줄. 날짜가 바뀌면 날짜 줄을 넣는다 (Figma 02 "11월 12일 목요일") */
sealed interface ChatRow {
    val key: String

    data class Date(val date: LocalDate) : ChatRow {
        override val key: String get() = "date:$date"
    }

    /**
     * @param showSender 상대 메시지 묶음의 첫 줄: 아바타·이름을 보인다
     * @param showTime 같은 사람·같은 분의 묶음 마지막 줄에만 시각을 보인다 (카카오톡 방식)
     */
    data class Message(val message: ChatMessage, val showSender: Boolean, val showTime: Boolean) : ChatRow {
        override val key: String get() = message.key
    }
}

/** seq 오름차순 메시지를 화면 줄로 바꾼다. 날짜·분은 [zone](서울) 기준 */
fun buildChatRows(messages: List<ChatMessage>, zone: ZoneId): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 8)
    messages.forEachIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        val date = message.sentAt.atZone(zone).toLocalDate()
        val newDate = previous == null || previous.sentAt.atZone(zone).toLocalDate() != date
        if (newDate) rows += ChatRow.Date(date)

        val showSender = !message.isSystem && (newDate || !sameRun(previous, message, zone))
        val showTime = !message.isSystem && !sameRun(message, next, zone)
        rows += ChatRow.Message(message, showSender, showTime)
    }
    return rows
}

/** 두 메시지가 같은 사람·같은 분·같은 날이고 둘 다 일반 메시지면 한 묶음이다 */
private fun sameRun(a: ChatMessage?, b: ChatMessage?, zone: ZoneId): Boolean {
    if (a == null || b == null || a.isSystem || b.isSystem) return false
    return a.senderId == b.senderId && sameMinute(a.sentAt, b.sentAt, zone)
}

private fun sameMinute(a: Instant, b: Instant, zone: ZoneId): Boolean =
    a.atZone(zone).truncatedTo(ChronoUnit.MINUTES) == b.atZone(zone).truncatedTo(ChronoUnit.MINUTES)
