package com.famlog.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.famlog.R
import com.famlog.core.data.StartPrompt
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/**
 * SCR-00 강제 업데이트. 최소 지원 버전보다 낮으면(시작 때 앱 설정, 또는 어느 API든 426) 여기로 오고 닫을 수 없다.
 * 뒤로가기는 앱을 닫는다.
 */
@Composable
fun ForceUpdateScreen(onOpenStore: (Context) -> Unit = Context::openStore) {
    val context = LocalContext.current
    val colors = FamTheme.colors
    BackHandler { (context as? android.app.Activity)?.finish() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgDefault)
            .systemBarsPadding()
            .padding(FamDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(FamDimens.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_bar_brand),
            style = FamTheme.type.titleMedium,
            color = colors.brandPrimary,
        )
        Text(
            stringResource(R.string.force_update_title),
            style = FamTheme.type.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.force_update_body),
            style = FamTheme.type.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        FamButton(
            text = stringResource(R.string.force_update_button),
            onClick = { onOpenStore(context) },
            modifier = Modifier.fillMaxWidth().padding(top = FamDimens.lg),
        )
    }
}

/** 앱 시작 안내: 새 공지 [확인] / 선택 업데이트 [업데이트]·[나중에] */
@Composable
fun StartPromptDialog(prompt: StartPrompt, onDone: () -> Unit, onOpenStore: (Context) -> Unit = Context::openStore) {
    val context = LocalContext.current
    val colors = FamTheme.colors
    when (prompt) {
        is StartPrompt.Notice -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.notice_title)) },
            text = { Text(prompt.text, style = FamTheme.type.bodyMedium, color = colors.textPrimary) },
            confirmButton = {
                TextButton(onClick = onDone) { Text(stringResource(R.string.notice_confirm), color = colors.brandPrimary) }
            },
            containerColor = colors.bgDefault,
        )
        StartPrompt.OptionalUpdate -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.optional_update_title)) },
            text = { Text(stringResource(R.string.optional_update_body), style = FamTheme.type.bodyMedium, color = colors.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    onDone()
                    onOpenStore(context)
                }) { Text(stringResource(R.string.optional_update_button), color = colors.brandPrimary) }
            },
            dismissButton = {
                TextButton(onClick = onDone) { Text(stringResource(R.string.optional_update_later), color = colors.textSecondary) }
            },
            containerColor = colors.bgDefault,
        )
    }
}

/** Play 스토어의 이 앱 페이지. 스토어 앱이 없으면 웹으로 */
fun Context.openStore() {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(market)
    } catch (e: ActivityNotFoundException) {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
