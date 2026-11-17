package com.famlog.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.component.GroupAvatar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.GroupSummary

/** SCR-10 채팅 탭 (Figma 01 `6:46`). 이야기 배너(v1.1)·검색(CHAT-09, P2)은 아직 없다 */
@Composable
fun ChatScreen(
    onOpenRoom: (Long) -> Unit,
    onNewGroup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    ChatListContent(state, onOpenRoom, onNewGroup, modifier)
}

@Composable
fun ChatListContent(
    state: ChatListUiState,
    onOpenRoom: (Long) -> Unit,
    onNewGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = FamDimens.screenPadding,
            end = FamDimens.screenPadding,
            top = FamDimens.sm,
            bottom = FamDimens.lg,
        ),
    ) {
        item {
            Text(
                text = stringResource(R.string.chat_list_section),
                style = FamTheme.type.caption,
                color = FamTheme.colors.textTertiary,
                modifier = Modifier.padding(vertical = FamDimens.sm),
            )
        }
        items(state.groups, key = { it.id }) { group ->
            GroupChatRow(
                group = group,
                senderName = state.senderName(group),
                state = state,
                onClick = { onOpenRoom(group.id) },
            )
        }
        item { NewGroupRow(onClick = onNewGroup) }
    }
}

/** Figma List Item `4:81`: 썸네일 44 원, Title / Subtitle(1줄) / Meta */
@Composable
private fun GroupChatRow(group: GroupSummary, senderName: String?, state: ChatListUiState, onClick: () -> Unit) {
    val colors = FamTheme.colors
    val last = group.lastMessage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(vertical = FamDimens.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        GroupAvatar(name = group.name, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chat_list_title, group.name, group.memberCount),
                style = FamTheme.type.bodyMediumStrong,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = last?.let { previewText(it, senderName) } ?: stringResource(R.string.chat_list_empty_message),
                style = FamTheme.type.bodyMedium,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            if (last != null) {
                Text(
                    text = formatListTime(last.sentAt, state.today),
                    style = FamTheme.type.caption,
                    color = colors.textTertiary,
                )
            }
            if (group.unreadCount > 0) UnreadBadge(group.unreadCount)
        }
    }
}

/** 안 읽은 수 배지 (Figma 미디자인 → brand/primary 원형, Caption on-brand) */
@Composable
private fun UnreadBadge(count: Long) {
    val description = stringResource(R.string.chat_list_unread, count.toInt())
    Box(
        modifier = Modifier
            .widthIn(min = 20.dp)
            .heightIn(min = 20.dp)
            .background(FamTheme.colors.brandPrimary, CircleShape)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
            style = FamTheme.type.caption,
            color = FamTheme.colors.textOnBrand,
        )
    }
}

@Composable
private fun NewGroupRow(onClick: () -> Unit) {
    val colors = FamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = FamDimens.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.surfaceMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.brandPrimary)
        }
        Column {
            Text(
                text = stringResource(R.string.group_create_action),
                style = FamTheme.type.bodyMediumStrong,
                color = colors.brandPrimary,
            )
            Text(
                text = stringResource(R.string.chat_list_new_group_hint),
                style = FamTheme.type.caption,
                color = colors.textTertiary,
            )
        }
    }
}

private const val MAX_BADGE = 999
