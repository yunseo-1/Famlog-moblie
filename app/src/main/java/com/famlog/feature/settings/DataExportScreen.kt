package com.famlog.feature.settings

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.famlog.R
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.DataExport
import com.famlog.core.model.DataExportStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 내 데이터 내보내기 (ACC-02). 개인정보·보안과 준비 알림에서 들어온다 */
@Composable
fun DataExportScreen(onBack: () -> Unit, viewModel: DataExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watch() } }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is DataExportEvent.Message -> event.text
                is DataExportEvent.Download -> context.download(event.url, event.fileName)
            }
            snackbarHostState.showSnackbar(message.asString(context))
        }
    }
    DataExportContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onRequest = viewModel::request,
        onDownload = viewModel::download,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun DataExportContent(
    state: DataExportUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequest: () -> Unit,
    onDownload: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack, title = stringResource(R.string.export_title))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.md),
                verticalArrangement = Arrangement.spacedBy(FamDimens.md),
            ) {
                Text(stringResource(R.string.export_intro), style = FamTheme.type.bodyMedium, color = colors.textPrimary)
                listOf(
                    R.string.export_item_messages,
                    R.string.export_item_media,
                    R.string.export_item_schedules,
                    R.string.export_item_places,
                    R.string.export_item_profile,
                ).forEach { Text("· " + stringResource(it), style = FamTheme.type.bodyMedium, color = colors.textPrimary) }
                Text(stringResource(R.string.export_rules), style = FamTheme.type.caption, color = colors.textTertiary)
                HorizontalDivider(color = colors.borderDefault)
                when {
                    state.loading -> CircularProgressIndicator(color = colors.brandPrimary)
                    state.loadFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.export_load_failed),
                            style = FamTheme.type.bodyMedium,
                            color = colors.statusDanger,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                        }
                    }
                    else -> StatusBlock(state.export)
                }
            }
            val export = state.export
            when {
                state.canRequest -> FamButton(
                    text = stringResource(if (export?.status == DataExportStatus.FAILED) R.string.export_request_again else R.string.export_request),
                    onClick = onRequest,
                    loading = state.requesting,
                    modifier = Modifier.fillMaxWidth().padding(FamDimens.screenPadding).navigationBarsPadding(),
                )
                export?.status == DataExportStatus.READY -> FamButton(
                    text = stringResource(R.string.export_download),
                    onClick = onDownload,
                    loading = state.downloading,
                    modifier = Modifier.fillMaxWidth().padding(FamDimens.screenPadding).navigationBarsPadding(),
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StatusBlock(export: DataExport?) {
    val colors = FamTheme.colors
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        val (title, body) = when (export?.status) {
            null -> R.string.export_status_none to null
            DataExportStatus.REQUESTED, DataExportStatus.PROCESSING -> R.string.export_status_preparing to
                stringResource(R.string.export_status_preparing_body)
            DataExportStatus.READY -> R.string.export_status_ready to stringResource(
                R.string.export_status_ready_body,
                export.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }.orEmpty(),
                export.expiresAt?.let(::formatDeadline).orEmpty(),
            )
            DataExportStatus.FAILED -> R.string.export_status_failed to stringResource(R.string.export_status_failed_body)
            DataExportStatus.EXPIRED -> R.string.export_status_expired to stringResource(R.string.export_status_expired_body)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
            if (export?.status?.preparing == true) {
                CircularProgressIndicator(color = colors.brandPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
            Text(
                stringResource(title),
                style = FamTheme.type.titleMedium,
                color = if (export?.status == DataExportStatus.FAILED) colors.statusDanger else colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        body?.let { Text(it, style = FamTheme.type.bodyMedium, color = colors.textSecondary) }
    }
}

private val deadlineFormatter = DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

private fun formatDeadline(instant: Instant): String = deadlineFormatter.format(instant)

/**
 * Android 10 이상은 시스템 다운로드로 '다운로드' 폴더에 받는다(권한 없음, 알림에 진행·완료).
 * 9 이하는 공용 폴더에 쓰려면 저장소 권한이 필요해서 묻지 않고 브라우저로 연다 (사양서 08 §8.6 최소 권한)
 */
private fun Context.download(url: String, fileName: String): UiText {
    val uri = Uri.parse(url)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setMimeType(ZIP_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        getSystemService(DownloadManager::class.java).enqueue(request)
        UiText.Res(R.string.export_download_started)
    } else {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            UiText.Res(R.string.export_download_browser)
        } catch (e: ActivityNotFoundException) {
            UiText.Res(R.string.export_download_no_browser)
        }
    }
}

private const val ZIP_MIME = "application/zip"
