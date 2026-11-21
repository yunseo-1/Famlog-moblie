package com.famlog.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.GroupAvatar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.MessageType
import com.famlog.core.model.Reaction
import com.famlog.core.model.ReplyPreview
import com.famlog.core.model.SendState
import com.famlog.feature.place.PlaceMessageCard

/** 날짜 줄 (Caption tertiary, 가운데) */
@Composable
fun DateSeparator(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = FamTheme.type.caption,
        color = FamTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FamDimens.sm),
    )
}

/** 시스템 메시지: 가운데 pill, bg surface/muted, Caption secondary (Figma 02-2) */
@Composable
fun SystemMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = FamTheme.type.caption,
            color = FamTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(FamTheme.colors.surfaceMuted, CircleShape)
                .padding(horizontal = FamDimens.md, vertical = FamDimens.xs),
        )
    }
}

/**
 * Figma Chat Bubble `4:37`. Other = 아바타 32 + 이름 + bubble/other(왼쪽), Mine = bubble/mine(오른쪽).
 * 말풍선 옆에 안 읽은 인원 숫자와 시각(묶음의 마지막 줄만)을 둔다. 길게 누르면 메뉴.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    mine: Boolean,
    senderName: String?,
    replyName: String?,
    showSender: Boolean,
    showTime: Boolean,
    myUserId: Long?,
    onLongPress: () -> Unit,
    onReactionClick: (Reaction) -> Unit,
    modifier: Modifier = Modifier,
    mediaBaseUrl: String = "",
    onOpenMedia: (index: Int) -> Unit = {},
    onOpenPlace: (placeId: Long) -> Unit = {},
    highlights: List<String> = emptyList(),
) {
    val colors = FamTheme.colors
    val unknownName = stringResource(R.string.chat_unknown_member)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            if (showSender) {
                // 나간 구성원은 첫 글자("나")가 "나 자신"으로 읽히지 않게 "?"로
                GroupAvatar(name = senderName ?: UNKNOWN_AVATAR, size = 32.dp)
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(FamDimens.sm))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine && showSender) {
                Text(
                    text = senderName ?: unknownName,
                    style = FamTheme.type.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = FamDimens.xs),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
                if (mine) MessageMeta(message, showTime, alignEnd = true)
                val place = message.place?.takeIf { !message.deleted }
                if (message.isMedia && !message.deleted) {
                    MediaGrid(
                        message = message,
                        baseUrl = mediaBaseUrl,
                        onOpen = onOpenMedia,
                        onLongPress = onLongPress,
                    )
                } else if (place != null) {
                    PlaceMessageCard(
                        card = place,
                        onClick = { onOpenPlace(place.placeId) },
                        onLongPress = onLongPress,
                    )
                } else Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(FamDimens.radiusLg))
                        .background(if (mine) colors.bubbleMine else colors.bubbleOther)
                        .combinedClickable(
                            onClick = { if (message.sendState == SendState.FAILED) onLongPress() },
                            onLongClick = onLongPress,
                        )
                        .padding(horizontal = FamDimens.md, vertical = FamDimens.sm)
                        .widthIn(max = FamDimens.bubbleMaxTextWidth),
                ) {
                    message.replyTo?.takeIf { !message.deleted }?.let { ReplyQuote(it, replyName) }
                    MessageBody(message, highlights)
                }
                if (!mine) MessageMeta(message, showTime, alignEnd = false)
            }
            if (message.reactions.isNotEmpty()) {
                ReactionRow(message.reactions, myUserId, onReactionClick, Modifier.padding(top = FamDimens.xs))
            }
        }
    }
}

@Composable
private fun MessageBody(message: ChatMessage, highlights: List<String>) {
    val colors = FamTheme.colors
    val (text, muted) = when {
        message.deleted -> stringResource(R.string.chat_message_deleted) to true
        message.type == MessageType.TEXT -> message.body.orEmpty() to false
        else -> stringResource(R.string.chat_message_unsupported) to true
    }
    Text(
        text = if (muted || highlights.isEmpty()) AnnotatedString(text) else highlight(text, highlights, colors.textHighlight),
        style = FamTheme.type.bodyMedium,
        color = if (muted) colors.textTertiary else colors.textPrimary,
        fontStyle = if (message.deleted) FontStyle.Italic else null,
    )
}

/** 원문 보기: 추출 부분([spans])마다 배경을 칠한다. 원문에 없는 부분은 건너뛴다 */
internal fun highlight(text: String, spans: List<String>, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.filter { it.isNotBlank() }.forEach { span ->
        var from = text.indexOf(span)
        while (from >= 0) {
            addStyle(SpanStyle(background = color), from, from + span.length)
            from = text.indexOf(span, from + span.length)
        }
    }
}

/** 답장 원문: 왼쪽 막대 + 보낸 사람 + 본문 1줄. 들어오기 전 메시지면 "이전 메시지" */
@Composable
private fun ReplyQuote(reply: ReplyPreview, name: String?) {
    val colors = FamTheme.colors
    val body = when {
        reply.deleted -> stringResource(R.string.chat_message_deleted)
        reply.body == null -> stringResource(R.string.chat_reply_unavailable)
        else -> reply.body
    }
    Row(
        modifier = Modifier
            .padding(bottom = FamDimens.xs)
            .heightIn(min = 32.dp),
    ) {
        Box(
            Modifier
                .width(2.dp)
                .heightIn(min = 32.dp)
                .background(colors.borderStrong),
        )
        Column(Modifier.padding(start = FamDimens.sm)) {
            if (name != null) {
                Text(text = name, style = FamTheme.type.caption, color = colors.textSecondary)
            }
            Text(
                text = body,
                style = FamTheme.type.caption,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 안 읽은 인원(brand) + 시각, 또는 전송 상태 */
@Composable
private fun MessageMeta(message: ChatMessage, showTime: Boolean, alignEnd: Boolean) {
    val colors = FamTheme.colors
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        when (message.sendState) {
            SendState.SENDING -> Icon(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = stringResource(R.string.chat_sending),
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            SendState.FAILED -> Icon(
                painter = painterResource(R.drawable.ic_alert),
                contentDescription = stringResource(R.string.chat_failed),
                tint = colors.statusDanger,
                modifier = Modifier.size(18.dp),
            )
            SendState.SENT -> {
                if (message.unreadCount > 0 && !message.isSystem) {
                    val description = stringResource(R.string.chat_unread_count, message.unreadCount)
                    Text(
                        text = message.unreadCount.toString(),
                        style = FamTheme.type.caption,
                        color = colors.brandPrimary,
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
                if (showTime) {
                    Text(text = formatTime(message.sentAt), style = FamTheme.type.caption, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun ReactionRow(
    reactions: List<Reaction>,
    myUserId: Long?,
    onClick: (Reaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        reactions.forEach { reaction ->
            val mine = myUserId != null && myUserId in reaction.userIds
            val mineLabel = stringResource(R.string.chat_reaction_mine)
            Text(
                text = stringResource(R.string.chat_reaction_count, reaction.emoji.glyph, reaction.userIds.size),
                style = FamTheme.type.caption,
                color = if (mine) colors.brandPrimary else colors.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (mine) colors.brandSubtle else colors.bgDefault)
                    .border(1.dp, if (mine) colors.brandPrimary else colors.borderDefault, CircleShape)
                    .clickable(role = Role.Button) { onClick(reaction) }
                    .semantics {
                        selected = mine
                        if (mine) stateDescription = mineLabel
                    }
                    .padding(horizontal = FamDimens.sm, vertical = 2.dp),
            )
        }
    }
}

/** 답장 중 표시줄: "엄마에게 답장" + 원문 1줄 + [취소] */
@Composable
fun ReplyBar(name: String, body: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FamTheme.colors
    Column(modifier.background(colors.bgDefault)) {
        HorizontalDivider(color = colors.borderDefault)
        Row(
            modifier = Modifier.padding(start = FamDimens.lg, end = FamDimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = FamDimens.sm)) {
                Text(
                    text = stringResource(R.string.chat_reply_to, name),
                    style = FamTheme.type.label,
                    color = colors.brandPrimary,
                )
                Text(
                    text = body,
                    style = FamTheme.type.caption,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.chat_reply_cancel),
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Figma Chat Input `4:92`: 상단 1px border, padding 8, gap 8. 입력창(surface/muted, radius full, padding 8/16) · send(brand).
 * plus = 사진·동영상 보내기 (Photo Picker).
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
) {
    val colors = FamTheme.colors
    Column(modifier.background(colors.bgDefault)) {
        HorizontalDivider(color = colors.borderDefault)
        Row(
            modifier = Modifier.padding(FamDimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
        ) {
            if (onAttach != null) {
                IconButton(onClick = onAttach) {
                    Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = stringResource(R.string.chat_attach),
                        tint = colors.textSecondary,
                    )
                }
            }
            val placeholder = stringResource(R.string.chat_input_placeholder)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = FamTheme.type.bodyMedium.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.brandPrimary),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .semantics { contentDescription = placeholder },
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .background(colors.surfaceMuted, RoundedCornerShape(20.dp))
                            .padding(horizontal = FamDimens.lg, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(text = placeholder, style = FamTheme.type.bodyMedium, color = colors.textTertiary)
                        }
                        inner()
                    }
                },
            )
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.chat_send),
                    tint = if (canSend) colors.brandPrimary else colors.textTertiary,
                )
            }
        }
    }
}

private const val UNKNOWN_AVATAR = "?"
