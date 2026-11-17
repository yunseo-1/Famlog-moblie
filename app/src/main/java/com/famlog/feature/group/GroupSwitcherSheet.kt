package com.famlog.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.GroupAvatar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.GroupSummary

data class GroupSwitcherState(
    val groups: List<GroupSummary> = emptyList(),
    val currentGroupId: Long? = null,
    /** 전환 요청 중인 그룹 */
    val switchingGroupId: Long? = null,
    val inviting: Boolean = false,
) {
    val currentGroup: GroupSummary? get() = groups.firstOrNull { it.id == currentGroupId }
}

/** SCR-70 가족 그룹 전환 (Figma 07 `16:396`) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSwitcherSheet(
    state: GroupSwitcherState,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onInvite: () -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = FamTheme.colors.bgDefault,
        dragHandle = {
            BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = FamTheme.colors.surfacePlaceholder)
        },
    ) {
        GroupSwitcherContent(state, onSelect, onInvite, onCreate, onJoin)
    }
}

@Composable
fun GroupSwitcherContent(
    state: GroupSwitcherState,
    onSelect: (Long) -> Unit,
    onInvite: () -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = FamDimens.lg, end = FamDimens.lg, bottom = FamDimens.xxl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(FamDimens.xs),
    ) {
        Text(
            text = stringResource(R.string.group_switcher_title),
            style = FamTheme.type.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier
                .padding(vertical = FamDimens.sm)
                .semantics { heading() },
        )
        state.groups.forEach { group ->
            GroupRow(
                group = group,
                current = group.id == state.currentGroupId,
                switching = group.id == state.switchingGroupId,
                onClick = { onSelect(group.id) },
            )
        }
        HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.xs))
        if (state.currentGroup?.myRole?.canInvite == true) {
            ActionRow(
                text = stringResource(R.string.group_switcher_invite, state.currentGroup!!.name),
                loading = state.inviting,
                onClick = onInvite,
            )
        }
        ActionRow(text = stringResource(R.string.group_create_action), onClick = onCreate)
        ActionRow(text = stringResource(R.string.group_join_action), onClick = onJoin)
    }
}

@Composable
private fun GroupRow(group: GroupSummary, current: Boolean, switching: Boolean, onClick: () -> Unit) {
    val colors = FamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (current) colors.brandSubtle else colors.bgDefault, RoundedCornerShape(FamDimens.radiusMd))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = current }
            .heightIn(min = 64.dp)
            .padding(horizontal = FamDimens.lg, vertical = FamDimens.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        GroupAvatar(name = group.name)
        Column(Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = FamTheme.type.bodyMediumStrong,
                color = if (current) colors.brandPrimary else colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.group_member_count, group.memberCount),
                style = FamTheme.type.caption,
                color = colors.textTertiary,
            )
        }
        when {
            switching -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.brandPrimary, strokeWidth = 2.dp)
            current -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.group_switcher_current),
                tint = colors.brandPrimary,
            )
        }
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit, loading: Boolean = false) {
    val colors = FamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = FamDimens.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), color = colors.brandPrimary, strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.textPrimary)
        }
        Text(text = text, style = FamTheme.type.bodyMedium, color = colors.textPrimary)
    }
}
