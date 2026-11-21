package com.famlog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/** 그룹 설정 (SCR-12 채팅방 서랍 → 그룹 설정): AI 분석(SET-01), 그룹 나가기·삭제(GRP-05). [onLeft]는 나가거나 지운 뒤 */
@Composable
fun GroupSettingsScreen(onBack: () -> Unit, onLeft: () -> Unit, viewModel: GroupSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                SettingsEvent.Left -> onLeft()
            }
        }
    }
    GroupSettingsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onAiEnabledChange = viewModel::setAiEnabled,
        onAiOptOutChange = viewModel::setAiOptOut,
        onShowNotice = viewModel::showNotice,
        onLeave = viewModel::openLeave,
        onDelete = viewModel::openDelete,
        snackbarHostState = snackbarHostState,
    )
    state.notice?.let { notice ->
        AiNoticeSheet(state = notice, onConfirm = viewModel::confirmNotice, onOptOut = {}, onDismiss = viewModel::closeNotice)
    }
    state.leave?.let { dialog ->
        LeaveDialogView(state.groupName, dialog, onConfirm = viewModel::confirmLeave, onDismiss = viewModel::closeLeave)
    }
    state.delete?.let { dialog ->
        DeleteDialogView(
            groupName = state.groupName,
            dialog = dialog,
            canDelete = viewModel.canDelete(dialog),
            onNameChange = viewModel::onDeleteNameChange,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::closeDelete,
        )
    }
}

/** 나가기 확인. 다른 가족이 있는 그룹장은 넘기라는 안내만 */
@Composable
fun LeaveDialogView(groupName: String, dialog: LeaveDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = FamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_leave_title, groupName)) },
        text = {
            Text(
                stringResource(
                    when {
                        dialog.ownerMustTransfer -> R.string.group_leave_owner_body
                        dialog.alone -> R.string.group_leave_alone_body
                        else -> R.string.group_leave_body
                    },
                ),
                style = FamTheme.type.bodyMedium,
                color = colors.textPrimary,
            )
        },
        confirmButton = {
            if (dialog.ownerMustTransfer) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.group_settings_ok), color = colors.brandPrimary) }
            } else {
                TextButton(onClick = onConfirm, enabled = !dialog.busy) {
                    Text(stringResource(R.string.group_leave_confirm), color = colors.statusDanger)
                }
            }
        },
        dismissButton = if (dialog.ownerMustTransfer) {
            null
        } else {
            { TextButton(onClick = onDismiss, enabled = !dialog.busy) { Text(stringResource(R.string.group_settings_cancel), color = colors.textSecondary) } }
        },
        containerColor = colors.bgDefault,
    )
}

/** 그룹 삭제 확인: 그룹 이름을 그대로 입력해야 [삭제] */
@Composable
fun DeleteDialogView(
    groupName: String,
    dialog: DeleteDialog,
    canDelete: Boolean,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FamDimens.md)) {
                Text(stringResource(R.string.group_delete_body, groupName), style = FamTheme.type.bodyMedium, color = colors.textPrimary)
                FamTextField(value = dialog.name, onValueChange = onNameChange, label = stringResource(R.string.group_delete_field), enabled = !dialog.busy)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canDelete && !dialog.busy) {
                Text(stringResource(R.string.group_delete_confirm), color = if (canDelete) colors.statusDanger else colors.textTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.busy) { Text(stringResource(R.string.group_settings_cancel), color = colors.textSecondary) }
        },
        containerColor = colors.bgDefault,
    )
}

@Composable
fun GroupSettingsContent(
    state: GroupSettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAiEnabledChange: (Boolean) -> Unit,
    onAiOptOutChange: (Boolean) -> Unit,
    onShowNotice: () -> Unit,
    onLeave: () -> Unit = {},
    onDelete: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack, title = stringResource(R.string.group_settings_title))
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(FamDimens.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brandPrimary)
                }
                state.loadFailed -> Row(
                    Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.group_settings_failed),
                        style = FamTheme.type.bodyMedium,
                        color = colors.statusDanger,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                    }
                }
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
                    verticalArrangement = Arrangement.spacedBy(FamDimens.sm),
                ) {
                    SectionTitle(stringResource(R.string.group_settings_ai_section))
                    SwitchRow(
                        title = stringResource(R.string.group_settings_ai),
                        description = stringResource(
                            if (state.isAdmin) R.string.group_settings_ai_desc else R.string.group_settings_ai_admin_only,
                        ),
                        checked = state.aiEnabled,
                        enabled = state.isAdmin && !state.savingAi,
                        onCheckedChange = onAiEnabledChange,
                    )
                    if (state.aiPaused) {
                        Text(
                            stringResource(R.string.group_settings_ai_paused),
                            style = FamTheme.type.caption,
                            color = colors.textSecondary,
                        )
                    }
                    SwitchRow(
                        title = stringResource(R.string.group_settings_opt_out),
                        description = stringResource(R.string.group_settings_opt_out_desc),
                        checked = state.aiOptOut,
                        enabled = !state.savingOptOut,
                        onCheckedChange = onAiOptOutChange,
                    )
                    TextButton(onClick = onShowNotice) {
                        Text(stringResource(R.string.group_settings_show_notice), style = FamTheme.type.label, color = colors.brandPrimary)
                    }
                    if (state.isAdmin && state.members.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.sm))
                        SectionTitle(stringResource(R.string.group_settings_members_opt_out))
                        Text(
                            stringResource(R.string.group_settings_members_opt_out_desc),
                            style = FamTheme.type.caption,
                            color = colors.textTertiary,
                        )
                        state.members.sortedByDescending { it.userId == state.myUserId }.forEach { member ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = FamDimens.minTouchTarget),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(member.displayName, style = FamTheme.type.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                Text(
                                    stringResource(
                                        when (member.aiOptOut) {
                                            true -> R.string.group_settings_member_opted_out
                                            false -> R.string.group_settings_member_analyzed
                                            null -> R.string.group_settings_member_unknown
                                        },
                                    ),
                                    style = FamTheme.type.label,
                                    color = if (member.aiOptOut == true) colors.textPrimary else colors.textTertiary,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.sm))
                    SectionTitle(stringResource(R.string.group_settings_danger_section))
                    DangerRow(stringResource(R.string.group_leave), onLeave)
                    if (state.isOwner) DangerRow(stringResource(R.string.group_delete), onDelete)
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DangerRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = FamTheme.type.bodyLarge,
        color = FamTheme.colors.statusDanger,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = FamTheme.type.label,
        color = FamTheme.colors.textSecondary,
        modifier = Modifier.padding(top = FamDimens.sm).semantics { heading() },
    )
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = FamTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.minTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FamTheme.type.bodyLarge, color = colors.textPrimary)
            Text(description, style = FamTheme.type.caption, color = colors.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.brandPrimary),
        )
    }
}
