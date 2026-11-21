package com.famlog.feature.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.GroupAvatar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.GroupRole

/** 구성원 시트 입력 */
data class MemberSheetActions(
    val onAction: (MemberAction) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

fun MemberViewModel.sheetActions() = MemberSheetActions(::perform, ::confirm, ::cancelConfirm, ::close)

/** GRP-05 구성원 시트: 이름·권한 + 내가 할 수 있는 것만. 그룹장 넘기기·내보내기는 확인을 받는다 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSheet(state: MemberSheetState, actions: MemberSheetActions) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        MemberSheetContent(state, actions)
    }
    state.confirm?.let { action -> MemberConfirmDialog(state, action, actions) }
}

@Composable
fun MemberSheetContent(state: MemberSheetState, actions: MemberSheetActions) {
    val colors = FamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = FamDimens.xl)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FamDimens.lg, vertical = FamDimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
        ) {
            GroupAvatar(name = state.member.displayName, size = 40.dp)
            Column {
                Text(
                    state.member.displayName,
                    style = FamTheme.type.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(state.member.role.labelRes()), style = FamTheme.type.caption, color = colors.textTertiary)
            }
        }
        state.actions.forEach { action ->
            Text(
                text = stringResource(action.labelRes()),
                style = FamTheme.type.bodyLarge,
                color = if (action == MemberAction.REMOVE) colors.statusDanger else colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FamDimens.minTouchTarget)
                    .clickable(enabled = !state.busy, role = Role.Button) { actions.onAction(action) }
                    .padding(horizontal = FamDimens.lg, vertical = FamDimens.md),
            )
        }
    }
}

@Composable
private fun MemberConfirmDialog(state: MemberSheetState, action: MemberAction, actions: MemberSheetActions) {
    val colors = FamTheme.colors
    val name = state.member.displayName
    val (title, body, confirm) = when (action) {
        MemberAction.TRANSFER_OWNER -> Triple(
            stringResource(R.string.member_transfer_title, name),
            stringResource(R.string.member_transfer_body),
            stringResource(R.string.member_transfer_confirm),
        )
        else -> Triple(
            stringResource(R.string.member_remove_title, name),
            stringResource(R.string.member_remove_body),
            stringResource(R.string.member_remove_confirm),
        )
    }
    AlertDialog(
        onDismissRequest = actions.onCancelConfirm,
        title = { Text(title) },
        text = { Text(body, style = FamTheme.type.bodyMedium, color = colors.textPrimary) },
        confirmButton = {
            TextButton(onClick = actions.onConfirm, enabled = !state.busy) {
                Text(confirm, color = if (action == MemberAction.REMOVE) colors.statusDanger else colors.brandPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCancelConfirm, enabled = !state.busy) {
                Text(stringResource(R.string.member_cancel), color = colors.textSecondary)
            }
        },
        containerColor = colors.bgDefault,
    )
}

private fun MemberAction.labelRes(): Int = when (this) {
    MemberAction.MAKE_ADMIN -> R.string.member_make_admin
    MemberAction.MAKE_MEMBER -> R.string.member_make_member
    MemberAction.TRANSFER_OWNER -> R.string.member_transfer
    MemberAction.REMOVE -> R.string.member_remove
    MemberAction.REPORT -> R.string.member_report
}

private fun GroupRole.labelRes(): Int = when (this) {
    GroupRole.OWNER -> R.string.chat_role_owner
    GroupRole.ADMIN -> R.string.chat_role_admin
    GroupRole.MEMBER -> R.string.member_role_member
}
