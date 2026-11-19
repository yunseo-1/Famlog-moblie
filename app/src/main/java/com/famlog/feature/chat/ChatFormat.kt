package com.famlog.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.famlog.R
import com.famlog.core.model.LastMessage
import com.famlog.core.model.MessageType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 날짜·시각은 서울 기준으로 보인다 (CLAUDE.md 3장) */
val SeoulZone: ZoneId = ZoneId.of("Asia/Seoul")

private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val monthDayFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy. M. d.", Locale.KOREAN)
private val separatorFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val separatorWithYearFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)

/** "오후 3:12" */
fun formatTime(instant: Instant): String = timeFormatter.format(instant.atZone(SeoulZone))

/** 채팅 탭 목록 시각: 오늘은 "오후 3:12", 어제는 "어제", 올해는 "11월 12일", 그 전은 "2025. 11. 12." */
@Composable
fun formatListTime(instant: Instant, today: LocalDate): String {
    val date = instant.atZone(SeoulZone).toLocalDate()
    return when {
        date == today -> formatTime(instant)
        date == today.minusDays(1) -> stringResource(R.string.chat_time_yesterday)
        date.year == today.year -> monthDayFormatter.format(date)
        else -> fullDateFormatter.format(date)
    }
}

/** 날짜 줄: 올해는 "11월 12일 목요일", 그 전은 연도를 붙인다 */
fun formatDateSeparator(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) separatorFormatter.format(date) else separatorWithYearFormatter.format(date)

/** 채팅 탭 미리보기 본문. 보낸 사람 이름은 [senderName]이 있을 때만 앞에 붙인다 */
@Composable
fun previewText(message: LastMessage, senderName: String?): String {
    val body = when {
        message.deleted -> stringResource(R.string.chat_message_deleted)
        message.type == MessageType.TEXT || message.type == MessageType.SYSTEM -> message.body.orEmpty()
        message.type == MessageType.IMAGE -> stringResource(R.string.chat_preview_image)
        message.type == MessageType.VIDEO -> stringResource(R.string.chat_preview_video)
        message.type == MessageType.CANDIDATE -> stringResource(R.string.chat_preview_candidate)
        message.type == MessageType.PLACE -> stringResource(R.string.chat_preview_place, message.body.orEmpty())
        else -> stringResource(R.string.chat_preview_other)
    }
    return if (senderName != null && message.type != MessageType.SYSTEM) {
        stringResource(R.string.chat_preview_sender, senderName, body)
    } else {
        body
    }
}
