package com.famlog.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.ReportReason
import com.famlog.core.model.ReportTargetType

/** 신고 시트 입력 */
data class ReportSheetActions(
    val onReasonChange: (ReportReason) -> Unit = {},
    val onDetailChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

fun ReportViewModel.sheetActions() = ReportSheetActions(::onReasonChange, ::onDetailChange, ::submit, ::close)

/** SCR-82 신고하기: 사유 고르기 + (기타면 필수) 내용 → [신고하기] */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(state: ReportSheetState, actions: ReportSheetActions) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        ReportContent(state, actions)
    }
}

@Composable
fun ReportContent(state: ReportSheetState, actions: ReportSheetActions) {
    val colors = FamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = FamDimens.lg, end = FamDimens.lg, bottom = FamDimens.xl)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(FamDimens.sm),
    ) {
        Text(
            stringResource(R.string.report_title),
            style = FamTheme.type.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(state.target.type.guideRes()), style = FamTheme.type.bodyMedium, color = colors.textSecondary)
        Column(Modifier.selectableGroup()) {
            state.target.reasons.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FamDimens.minTouchTarget)
                        .selectable(
                            selected = reason == state.reason,
                            enabled = !state.saving,
                            role = Role.RadioButton,
                            onClick = { actions.onReasonChange(reason) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = reason == state.reason,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = colors.brandPrimary),
                    )
                    Text(
                        stringResource(reason.labelRes()),
                        style = FamTheme.type.bodyLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(start = FamDimens.sm),
                    )
                }
            }
        }
        if (state.showErrors && state.reason == null) {
            Text(stringResource(R.string.report_reason_required), style = FamTheme.type.caption, color = colors.statusDanger)
        }
        FamTextField(
            value = state.detail,
            onValueChange = actions.onDetailChange,
            label = stringResource(if (state.detailRequired) R.string.report_detail_required else R.string.report_detail_optional),
            supportingText = stringResource(R.string.report_detail_count, state.detail.length, ReportViewModel.MAX_DETAIL),
            errorText = if (state.showErrors && state.detailMissing) stringResource(R.string.report_detail_missing) else null,
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = false,
            minLines = 3,
        )
        FamButton(
            text = stringResource(R.string.report_submit),
            onClick = actions.onSubmit,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = FamDimens.sm),
        )
    }
}

private fun ReportTargetType.guideRes(): Int = when (this) {
    ReportTargetType.MESSAGE -> R.string.report_guide_message
    ReportTargetType.MEDIA -> R.string.report_guide_media
    ReportTargetType.AI_OUTPUT -> R.string.report_guide_ai
    ReportTargetType.USER -> R.string.report_guide_user
}

private fun ReportReason.labelRes(): Int = when (this) {
    ReportReason.SPAM -> R.string.report_reason_spam
    ReportReason.HARASSMENT -> R.string.report_reason_harassment
    ReportReason.SEXUAL -> R.string.report_reason_sexual
    ReportReason.VIOLENCE -> R.string.report_reason_violence
    ReportReason.CHILD_SAFETY -> R.string.report_reason_child_safety
    ReportReason.PRIVACY -> R.string.report_reason_privacy
    ReportReason.AI_INACCURATE -> R.string.report_reason_ai_inaccurate
    ReportReason.OTHER -> R.string.report_reason_other
}
