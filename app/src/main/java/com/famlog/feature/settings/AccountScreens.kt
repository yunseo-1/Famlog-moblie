package com.famlog.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.Consent

// ---- SCR-80 설정 ----

/** 설정 (SCR-80, 채팅 탭 앱바 톱니바퀴). 알림·앱 잠금은 다음 단계(SET-02·SEC-01) */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val confirmLogout by viewModel.confirmLogout.collectAsStateWithLifecycle()
    val loggingOut by viewModel.loggingOut.collectAsStateWithLifecycle()
    SettingsContent(appVersion = viewModel.appVersion, onBack = onBack, onOpenPrivacy = onOpenPrivacy, onLogout = viewModel::askLogout)
    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.settings_logout_title),
            body = stringResource(R.string.settings_logout_body),
            confirm = stringResource(R.string.settings_logout),
            busy = loggingOut,
            onConfirm = viewModel::logout,
            onDismiss = viewModel::cancelLogout,
        )
    }
}

@Composable
fun SettingsContent(appVersion: String, onBack: () -> Unit, onOpenPrivacy: () -> Unit, onLogout: () -> Unit) {
    val colors = FamTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bgDefault)) {
        FamTopBar(onBack = onBack, title = stringResource(R.string.settings_title))
        Column(Modifier.padding(horizontal = FamDimens.screenPadding)) {
            NavRow(stringResource(R.string.settings_privacy), onOpenPrivacy)
            HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.sm))
            NavRow(stringResource(R.string.settings_logout), onLogout)
            Text(
                stringResource(R.string.settings_version, appVersion),
                style = FamTheme.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = FamDimens.lg),
            )
        }
    }
}

// ---- SCR-81 개인정보·보안 ----

/** 개인정보·보안 (SCR-81): 동의 내역(철회), 내 데이터 내보내기, 웹 계정 삭제 안내, 계정 삭제. 로그인 기기는 다음 단계 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onOpenDataExport: () -> Unit,
    onOpenAccountDelete: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHostState.showSnackbar(it.asString(context)) } }
    PrivacyContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onWithdraw = viewModel::askWithdraw,
        onOpenWebDeletion = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.webDeletionUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onOpenDataExport = onOpenDataExport,
        onOpenAccountDelete = onOpenAccountDelete,
        snackbarHostState = snackbarHostState,
    )
    state.withdraw?.let { type ->
        ConfirmDialog(
            title = stringResource(R.string.privacy_withdraw_title, stringResource(consentTitleRes(type))),
            body = stringResource(if (type == Consent.LOCATION) R.string.privacy_withdraw_location_body else R.string.privacy_withdraw_ai_body),
            confirm = stringResource(R.string.privacy_withdraw),
            busy = state.withdrawing,
            onConfirm = viewModel::confirmWithdraw,
            onDismiss = viewModel::cancelWithdraw,
        )
    }
}

@Composable
fun PrivacyContent(
    state: PrivacyUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWithdraw: (String) -> Unit,
    onOpenWebDeletion: () -> Unit,
    onOpenAccountDelete: () -> Unit,
    onOpenDataExport: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack, title = stringResource(R.string.privacy_title))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
                verticalArrangement = Arrangement.spacedBy(FamDimens.sm),
            ) {
                SectionHeading(stringResource(R.string.privacy_consents))
                when {
                    state.loading -> CircularProgressIndicator(color = colors.brandPrimary, modifier = Modifier.padding(FamDimens.md))
                    state.loadFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.privacy_failed),
                            style = FamTheme.type.bodyMedium,
                            color = colors.statusDanger,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                        }
                    }
                    else -> state.consents.forEach { consent -> ConsentRow(consent, onWithdraw) }
                }
                HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(vertical = FamDimens.sm))
                SectionHeading(stringResource(R.string.privacy_account))
                NavRow(stringResource(R.string.privacy_data_export), onOpenDataExport)
                NavRow(stringResource(R.string.privacy_web_deletion), onOpenWebDeletion)
                NavRow(stringResource(R.string.privacy_delete_account), onOpenAccountDelete, danger = true)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ConsentRow(consent: Consent, onWithdraw: (String) -> Unit) {
    val colors = FamTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = FamDimens.minTouchTarget), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(consentTitleRes(consent.type)), style = FamTheme.type.bodyLarge, color = colors.textPrimary)
            Text(
                when {
                    consent.valid -> stringResource(R.string.privacy_agreed, consent.docVersion.orEmpty())
                    consent.agreed -> stringResource(R.string.privacy_reconsent)
                    else -> stringResource(R.string.privacy_not_agreed)
                },
                style = FamTheme.type.caption,
                color = colors.textTertiary,
            )
        }
        if (consent.agreed && consent.type in PrivacyViewModel.WITHDRAWABLE) {
            TextButton(onClick = { onWithdraw(consent.type) }) {
                Text(stringResource(R.string.privacy_withdraw), style = FamTheme.type.label, color = colors.statusDanger)
            }
        }
    }
}

private fun consentTitleRes(type: String): Int = when (type) {
    Consent.TERMS -> R.string.consent_terms
    Consent.PRIVACY -> R.string.consent_privacy
    Consent.LOCATION -> R.string.consent_location
    Consent.AI_ANALYSIS -> R.string.consent_ai
    else -> R.string.consent_overseas
}

// ---- 계정 삭제 (ACC-01) ----

@Composable
fun AccountDeleteScreen(onBack: () -> Unit, viewModel: AccountDeleteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHostState.showSnackbar(it.asString(context)) } }
    AccountDeleteContent(
        state = state,
        onBack = onBack,
        onDeleteMediaChange = viewModel::setDeleteMedia,
        onDelete = viewModel::ask,
        snackbarHostState = snackbarHostState,
    )
    if (state.confirm) {
        ConfirmDialog(
            title = stringResource(R.string.account_delete_confirm_title),
            body = stringResource(R.string.account_delete_confirm_body),
            confirm = stringResource(R.string.account_delete_button),
            busy = state.deleting,
            onConfirm = viewModel::delete,
            onDismiss = viewModel::cancel,
        )
    }
    state.ownerTransfer?.let { message -> OwnerTransferDialog(message, viewModel::dismissOwnerTransfer) }
}

@Composable
fun AccountDeleteContent(
    state: AccountDeleteUiState,
    onBack: () -> Unit,
    onDeleteMediaChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack, title = stringResource(R.string.account_delete_title))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
                verticalArrangement = Arrangement.spacedBy(FamDimens.md),
            ) {
                listOf(
                    R.string.account_delete_point_logout,
                    R.string.account_delete_point_restore,
                    R.string.account_delete_point_erase,
                    R.string.account_delete_point_messages,
                    R.string.account_delete_point_owner,
                ).forEach { Text("· " + stringResource(it), style = FamTheme.type.bodyMedium, color = colors.textPrimary) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = FamDimens.minTouchTarget)
                        .toggleable(value = state.deleteMedia, enabled = !state.deleting, role = Role.Checkbox, onValueChange = onDeleteMediaChange),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.deleteMedia,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = colors.brandPrimary),
                    )
                    Column(Modifier.padding(start = FamDimens.sm)) {
                        Text(stringResource(R.string.account_delete_media), style = FamTheme.type.bodyLarge, color = colors.textPrimary)
                        Text(stringResource(R.string.account_delete_media_desc), style = FamTheme.type.caption, color = colors.textTertiary)
                    }
                }
            }
            FamButton(
                text = stringResource(R.string.account_delete_button),
                onClick = onDelete,
                loading = state.deleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FamDimens.screenPadding)
                    .navigationBarsPadding(),
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun OwnerTransferDialog(message: UiText, onDismiss: () -> Unit) {
    val colors = FamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_delete_owner_title)) },
        text = {
            Text(
                message.asString() + "\n" + stringResource(R.string.account_delete_owner_hint),
                style = FamTheme.type.bodyMedium,
                color = colors.textPrimary,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.group_settings_ok), color = colors.brandPrimary) }
        },
        containerColor = colors.bgDefault,
    )
}

// ---- 공통 ----

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = FamTheme.type.label,
        color = FamTheme.colors.textSecondary,
        modifier = Modifier.padding(top = FamDimens.sm).semantics { heading() },
    )
}

@Composable
private fun NavRow(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Text(
        text,
        style = FamTheme.type.bodyLarge,
        color = if (danger) FamTheme.colors.statusDanger else FamTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = FamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = FamTheme.type.bodyMedium, color = colors.textPrimary) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(confirm, color = colors.statusDanger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.group_settings_cancel), color = colors.textSecondary) }
        },
        containerColor = colors.bgDefault,
    )
}
