package com.famlog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.AiNotice

/** ENABLE: 관리자가 켜기 전 · CONFIRM: 켜진 그룹의 구성원이 처음 볼 때 · VIEW: 설정에서 다시 보기 */
enum class AiNoticeMode { ENABLE, CONFIRM, VIEW }

data class AiNoticeSheetState(val notice: AiNotice, val mode: AiNoticeMode, val saving: Boolean = false)

/**
 * AI 분석 안내 (SET-01, 사양서 8.3 국외 이전 고지): 받는 곳·국가, 보내는 것·보내지 않는 것, 목적, 보관 기간, 거부 방법.
 * 문구는 서버가 준다(버전이 오르면 다시 안내). 버튼 없이 닫으면 [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiNoticeSheet(
    state: AiNoticeSheetState,
    onConfirm: () -> Unit,
    onOptOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        AiNoticeContent(state, onConfirm, onOptOut, onDismiss)
    }
}

@Composable
fun AiNoticeContent(
    state: AiNoticeSheetState,
    onConfirm: () -> Unit,
    onOptOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FamTheme.colors
    val notice = state.notice
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = FamDimens.lg, end = FamDimens.lg, bottom = FamDimens.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        Text(
            stringResource(
                when (state.mode) {
                    AiNoticeMode.ENABLE -> R.string.ai_notice_title_enable
                    AiNoticeMode.CONFIRM -> R.string.ai_notice_title_confirm
                    AiNoticeMode.VIEW -> R.string.ai_notice_title_view
                },
            ),
            style = FamTheme.type.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (state.mode == AiNoticeMode.ENABLE) R.string.ai_notice_intro_enable else R.string.ai_notice_intro),
            style = FamTheme.type.bodyMedium,
            color = colors.textSecondary,
        )
        NoticeItem(stringResource(R.string.ai_notice_recipient), stringResource(R.string.ai_notice_recipient_value, notice.recipient, notice.country))
        NoticeItem(stringResource(R.string.ai_notice_sent), notice.sentItems.joinToString("\n") { "· $it" })
        NoticeItem(stringResource(R.string.ai_notice_not_sent), notice.notSentItems.joinToString("\n") { "· $it" })
        NoticeItem(stringResource(R.string.ai_notice_purpose), notice.purpose)
        NoticeItem(stringResource(R.string.ai_notice_retention), notice.retention)
        NoticeItem(stringResource(R.string.ai_notice_refusal), notice.refusal)
        Text(stringResource(R.string.ai_notice_version, notice.version), style = FamTheme.type.caption, color = colors.textTertiary)
        when (state.mode) {
            AiNoticeMode.ENABLE -> {
                FamButton(stringResource(R.string.ai_notice_enable), onClick = onConfirm, loading = state.saving, modifier = Modifier.fillMaxWidth())
                FamButton(
                    stringResource(R.string.ai_notice_cancel),
                    onClick = onDismiss,
                    style = FamButtonStyle.GHOST,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AiNoticeMode.CONFIRM -> {
                FamButton(stringResource(R.string.ai_notice_confirm), onClick = onConfirm, loading = state.saving, modifier = Modifier.fillMaxWidth())
                FamButton(
                    stringResource(R.string.ai_notice_opt_out),
                    onClick = onOptOut,
                    style = FamButtonStyle.SECONDARY,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AiNoticeMode.VIEW -> FamButton(
                stringResource(R.string.ai_notice_close),
                onClick = onDismiss,
                style = FamButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NoticeItem(label: String, value: String) {
    if (value.isBlank()) return
    val colors = FamTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = FamTheme.type.label, color = colors.textTertiary)
        Text(value, style = FamTheme.type.bodyMedium, color = colors.textPrimary)
    }
}
