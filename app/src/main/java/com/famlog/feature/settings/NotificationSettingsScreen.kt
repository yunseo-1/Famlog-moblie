package com.famlog.feature.settings

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.famlog.feature.calendar.TimePickerDialogFor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 설정 > 알림 화면 입력 */
data class NotificationSettingsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPreviewHiddenChange: (Boolean) -> Unit = {},
    val onQuietHoursChange: (Boolean) -> Unit = {},
    val onQuietStartChange: (LocalTime) -> Unit = {},
    val onQuietEndChange: (LocalTime) -> Unit = {},
    val onGroupEnabledChange: (groupId: Long, enabled: Boolean) -> Unit = { _, _ -> },
    val onOpenSystemSettings: () -> Unit = {},
)

/** 알림 설정 (SET-02, SCR-80) */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHostState.showSnackbar(it.asString(context)) } }
    NotificationSettingsContent(
        state = state,
        actions = NotificationSettingsActions(
            onBack = onBack,
            onRetry = viewModel::load,
            onPreviewHiddenChange = viewModel::setPreviewHidden,
            onQuietHoursChange = viewModel::setQuietHours,
            onQuietStartChange = viewModel::setQuietStart,
            onQuietEndChange = viewModel::setQuietEnd,
            onGroupEnabledChange = viewModel::setGroupEnabled,
            onOpenSystemSettings = {
                // 채널별 켜기·끄기, 소리는 Android 앱 알림 설정에서
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ),
        snackbarHostState = snackbarHostState,
    )
}

private enum class QuietPicker { START, END }

@Composable
fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    var picker by remember { mutableStateOf<QuietPicker?>(null) }
    val settings = state.settings
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = actions.onBack, title = stringResource(R.string.notif_title))
            when {
                state.loading -> CircularProgressIndicator(color = colors.brandPrimary, modifier = Modifier.padding(FamDimens.screenPadding))
                state.loadFailed -> Row(
                    modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.notif_failed),
                        style = FamTheme.type.bodyMedium,
                        color = colors.statusDanger,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onRetry) {
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
                    Heading(stringResource(R.string.notif_content))
                    ToggleRow(
                        title = stringResource(R.string.notif_preview_hidden),
                        description = stringResource(R.string.notif_preview_hidden_desc),
                        checked = settings.previewHidden,
                        onCheckedChange = actions.onPreviewHiddenChange,
                    )
                    HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.sm))
                    Heading(stringResource(R.string.notif_quiet))
                    ToggleRow(
                        title = stringResource(R.string.notif_quiet_enabled),
                        description = stringResource(R.string.notif_quiet_desc),
                        checked = settings.quietHoursEnabled,
                        onCheckedChange = actions.onQuietHoursChange,
                    )
                    if (settings.quietHoursEnabled) {
                        TimeRow(stringResource(R.string.notif_quiet_start), settings.quietStart) { picker = QuietPicker.START }
                        TimeRow(stringResource(R.string.notif_quiet_end), settings.quietEnd) { picker = QuietPicker.END }
                    }
                    HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.sm))
                    Heading(stringResource(R.string.notif_groups))
                    Text(stringResource(R.string.notif_groups_desc), style = FamTheme.type.caption, color = colors.textTertiary)
                    state.groups.forEach { group ->
                        ToggleRow(
                            title = group.name,
                            description = stringResource(
                                when (group.muted) {
                                    null -> R.string.notif_group_unknown
                                    true -> R.string.notif_group_off
                                    false -> R.string.notif_group_on
                                },
                            ),
                            checked = group.muted == false,
                            enabled = group.muted != null,
                            onCheckedChange = { actions.onGroupEnabledChange(group.groupId, it) },
                        )
                    }
                    HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.sm))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = FamDimens.minTouchTarget)
                            .clickable(role = Role.Button, onClick = actions.onOpenSystemSettings)
                            .padding(vertical = FamDimens.sm),
                    ) {
                        Text(stringResource(R.string.notif_system), style = FamTheme.type.bodyLarge, color = colors.textPrimary)
                        Text(stringResource(R.string.notif_system_desc), style = FamTheme.type.caption, color = colors.textTertiary)
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    when (picker) {
        QuietPicker.START -> TimePickerDialogFor(settings.quietStart, { picker = null }, actions.onQuietStartChange)
        QuietPicker.END -> TimePickerDialogFor(settings.quietEnd, { picker = null }, actions.onQuietEndChange)
        null -> Unit
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = FamTheme.type.label,
        color = FamTheme.colors.textSecondary,
        modifier = Modifier.padding(top = FamDimens.sm).semantics { heading() },
    )
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
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

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    val colors = FamTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = FamTheme.type.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(quietTimeFormatter.format(time), style = FamTheme.type.bodyLarge, color = colors.brandPrimary)
    }
}

private val quietTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
