package com.famlog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.LoginSession
import java.time.Instant

/** 로그인 기기 관리 (AUTH-03). 개인정보·보안과 새 기기 로그인 알림에서 들어온다 */
@Composable
fun SessionsScreen(onBack: () -> Unit, viewModel: SessionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbarHostState.showSnackbar(it.asString(context)) } }
    SessionsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onRevoke = viewModel::ask,
        snackbarHostState = snackbarHostState,
    )
    state.confirm?.let { session ->
        val colors = FamTheme.colors
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(R.string.sessions_revoke_title, session.displayName())) },
            text = { Text(stringResource(R.string.sessions_revoke_body), style = FamTheme.type.bodyMedium, color = colors.textPrimary) },
            confirmButton = {
                TextButton(onClick = viewModel::revoke, enabled = !state.revoking) {
                    Text(stringResource(R.string.sessions_revoke), color = colors.statusDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancel, enabled = !state.revoking) {
                    Text(stringResource(R.string.group_settings_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.bgDefault,
        )
    }
}

@Composable
fun SessionsContent(
    state: SessionsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRevoke: (LoginSession) -> Unit,
    now: Instant = Instant.now(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack, title = stringResource(R.string.sessions_title))
            when {
                state.loading -> CircularProgressIndicator(color = colors.brandPrimary, modifier = Modifier.padding(FamDimens.screenPadding))
                state.loadFailed -> Row(
                    modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.sessions_failed),
                        style = FamTheme.type.bodyMedium,
                        color = colors.statusDanger,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            stringResource(R.string.sessions_guide),
                            style = FamTheme.type.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
                        )
                    }
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRow(session, now, onRevoke)
                        HorizontalDivider(color = colors.borderDefault, modifier = Modifier.padding(horizontal = FamDimens.screenPadding))
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SessionRow(session: LoginSession, now: Instant, onRevoke: (LoginSession) -> Unit) {
    val colors = FamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                Text(session.displayName(), style = FamTheme.type.bodyLarge, color = colors.textPrimary)
                if (session.current) {
                    Text(
                        stringResource(R.string.sessions_current),
                        style = FamTheme.type.caption,
                        color = colors.brandPrimary,
                        modifier = Modifier
                            .background(colors.brandSubtle, RoundedCornerShape(FamDimens.radiusSm))
                            .padding(horizontal = FamDimens.sm, vertical = 2.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.sessions_last_used, lastUsedText(session.lastUsedAt, now).asString()),
                style = FamTheme.type.caption,
                color = colors.textSecondary,
            )
            Text(
                stringResource(R.string.sessions_created, sessionDay(session.createdAt)),
                style = FamTheme.type.caption,
                color = colors.textTertiary,
            )
        }
        if (!session.current) {
            TextButton(onClick = { onRevoke(session) }) {
                Text(stringResource(R.string.sessions_revoke), style = FamTheme.type.label, color = colors.statusDanger)
            }
        }
    }
}

@Composable
private fun LoginSession.displayName(): String = deviceName ?: stringResource(
    when (platform) {
        "IOS" -> R.string.sessions_unknown_ios
        else -> R.string.sessions_unknown_android
    },
)
