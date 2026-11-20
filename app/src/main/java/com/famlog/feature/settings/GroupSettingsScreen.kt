package com.famlog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/** 그룹 설정 (SCR-12 채팅방 서랍 → 그룹 설정). 지금은 AI 분석(SET-01)만 */
@Composable
fun GroupSettingsScreen(onBack: () -> Unit, viewModel: GroupSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
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
        snackbarHostState = snackbarHostState,
    )
    state.notice?.let { notice ->
        AiNoticeSheet(state = notice, onConfirm = viewModel::confirmNotice, onOptOut = {}, onDismiss = viewModel::closeNotice)
    }
}

@Composable
fun GroupSettingsContent(
    state: GroupSettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAiEnabledChange: (Boolean) -> Unit,
    onAiOptOutChange: (Boolean) -> Unit,
    onShowNotice: () -> Unit,
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
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
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
