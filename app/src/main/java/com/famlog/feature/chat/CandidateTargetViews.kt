package com.famlog.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.famlog.R
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateTarget
import com.famlog.core.model.ScheduleTiming
import com.famlog.core.model.TargetAction
import com.famlog.core.model.TargetItem

/** 바꾸기 전·후 중 바뀐 부분만: "11/21(토) " + [오전 11:00] → [오후 12:00] + "" */
internal data class TimingChange(val prefix: String, val before: String, val after: String, val suffix: String)

/** 날짜만 바뀌면 날짜만, 시각만 바뀌면 시각만, 둘 다면 둘 다. 시각은 시작 시각(길이는 서버가 지킨다) */
internal fun timingChange(before: ScheduleTiming, after: ScheduleTiming, allDayLabel: String): TimingChange {
    val dateChanged = before.date != after.date
    val timeChanged = timeText(before, allDayLabel) != timeText(after, allDayLabel)
    return when {
        dateChanged && timeChanged -> TimingChange("", timingText(before, allDayLabel), timingText(after, allDayLabel), "")
        dateChanged -> TimingChange("", formatShortDate(before.date), formatShortDate(after.date), " ${timeText(after, allDayLabel)}")
        else -> TimingChange("${formatShortDate(before.date)} ", timeText(before, allDayLabel), timeText(after, allDayLabel), "")
    }
}

/** "11/21(토) 오전 11:00" · "11/21(토) 종일" */
internal fun timingText(timing: ScheduleTiming, allDayLabel: String): String =
    "${formatShortDate(timing.date)} ${timeText(timing, allDayLabel)}"

private fun timeText(timing: ScheduleTiming, allDayLabel: String): String =
    timing.startTime?.takeUnless { timing.allDay }?.let(::formatClock) ?: allDayLabel

/**
 * 변경·완료 후보 본문. 대상이 여럿이면 "어느 일정인가요?" 목록(바꿀 수 없는 일정은 흐리게),
 * 정해진 대상은 일정이면 "변경 전 → 후", 할 일이면 담당·기한.
 */
@Composable
internal fun TargetBody(
    target: CandidateTarget,
    pickedId: Long?,
    viewer: CandidateViewer,
    nameOf: (Long?) -> String?,
    onPick: (Long) -> Unit,
) {
    val colors = FamTheme.colors
    val allDay = stringResource(R.string.schedule_all_day)
    if (target.item == null) {
        Text(
            stringResource(if (target.entityType == CandidateEntity.TODO) R.string.candidate_target_pick_todo else R.string.candidate_target_pick_schedule),
            style = FamTheme.type.label,
            color = colors.textSecondary,
        )
        Column {
            target.choices.forEach { choice ->
                TargetChoiceRow(
                    title = choice.title ?: stringResource(R.string.candidate_no_title),
                    detail = choice.before?.let { timingText(it, allDay) } ?: todoText(choice, nameOf),
                    selected = choice.entityId == pickedId,
                    enabled = target.allows(choice, viewer.userId, viewer.role),
                    onClick = { onPick(choice.entityId) },
                )
            }
        }
    }
    val item = target.resolve(pickedId) ?: return
    when (target.action) {
        TargetAction.CHANGE -> {
            val before = item.before ?: return
            val after = item.after ?: return
            val change = timingChange(before, after, allDay)
            Text(
                buildAnnotatedString {
                    append(change.prefix)
                    withStyle(SpanStyle(color = colors.textTertiary, textDecoration = TextDecoration.LineThrough)) { append(change.before) }
                    append(" → ")
                    withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)) { append(change.after) }
                    append(change.suffix)
                },
                style = FamTheme.type.bodyMedium,
                color = colors.textSecondary,
            )
        }
        TargetAction.COMPLETE -> todoText(item, nameOf)?.let { Text(it, style = FamTheme.type.bodyMedium, color = colors.textSecondary) }
    }
}

/** "담당: 엄마 · 기한 11/22(일)까지" */
@Composable
private fun todoText(item: TargetItem, nameOf: (Long?) -> String?): String? = listOfNotNull(
    nameOf(item.assigneeId)?.let { stringResource(R.string.candidate_assignee, it) },
    item.dueDate?.let { stringResource(R.string.candidate_due, formatShortDate(it)) },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun TargetChoiceRow(title: String, detail: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = FamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.minTouchTarget)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = FamDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = colors.brandPrimary),
        )
        Column(Modifier.padding(start = FamDimens.sm)) {
            Text(title, style = FamTheme.type.bodyMediumStrong, color = if (enabled) colors.textPrimary else colors.textTertiary)
            detail?.let { Text(it, style = FamTheme.type.caption, color = colors.textTertiary) }
        }
    }
}
