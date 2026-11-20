package com.famlog.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonSize
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.designsystem.theme.categoryOf
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateFields
import com.famlog.core.model.CandidateType
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.DismissReason
import com.famlog.core.model.Member
import com.famlog.feature.place.labelRes
import com.famlog.feature.place.regionText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Figma AI Suggestion Card `4:80` (ARC-05). 근거 메시지 아래, 왼쪽 들여쓰기 40.
 * 헤더 · 제목 · 요약 · 추정 배지 · 근거 인용 · [등록][수정][무시] · "AI 제안".
 * [anchor]는 카드가 붙은 메시지(마지막 근거). 인용을 누르면 원문으로 가서 추출 부분을 강조한다.
 * 제안함에서 근거 메시지를 아직 안 받았으면 [anchor]가 null이고 인용 없이 "원문 보기"만 보인다.
 */
@Composable
fun CandidateCard(
    candidate: Candidate,
    anchor: ChatMessage?,
    members: List<Member>,
    busy: Boolean,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: (DismissReason?) -> Unit,
    onShowEvidence: () -> Unit,
    modifier: Modifier = Modifier,
    /** 채팅방은 아바타 폭만큼 들여 280까지, 제안함은 화면 너비 */
    indent: Boolean = true,
) {
    val colors = FamTheme.colors
    val (accent, background) = colors.categoryOf(candidate.type)
    val nameOf = { id: Long? -> members.firstOrNull { it.userId == id }?.displayName }
    Column(
        modifier = modifier
            .then(if (indent) Modifier.padding(start = FamDimens.chatIndent).widthIn(max = FamDimens.candidateCardWidth) else Modifier)
            .fillMaxWidth()
            .background(background, RoundedCornerShape(FamDimens.radiusLg))
            .border(1.dp, accent, RoundedCornerShape(FamDimens.radiusLg))
            .padding(FamDimens.lg),
        verticalArrangement = Arrangement.spacedBy(FamDimens.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(
                stringResource(candidate.type.foundRes()),
                style = FamTheme.type.label,
                color = accent,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            candidate.fields.title ?: stringResource(R.string.candidate_no_title),
            style = FamTheme.type.bodyMediumStrong,
            color = if (candidate.fields.title == null) colors.textTertiary else colors.textPrimary,
        )
        candidateMeta(candidate, nameOf).forEach { line ->
            Text(line, style = FamTheme.type.bodyMedium, color = colors.textSecondary)
        }
        inferredText(candidate)?.let { text -> InferredRow(text) }
        Evidence(
            quote = anchor?.body,
            meta = if (anchor != null) {
                stringResource(
                    R.string.candidate_evidence_meta,
                    nameOf(anchor.senderId) ?: stringResource(R.string.chat_unknown_member),
                    formatTime(anchor.sentAt),
                )
            } else {
                stringResource(R.string.candidate_show_original)
            },
            accent = accent,
            onClick = onShowEvidence,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
            verticalArrangement = Arrangement.spacedBy(FamDimens.xs),
        ) {
            FamButton(
                text = stringResource(R.string.candidate_approve),
                onClick = onApprove,
                size = FamButtonSize.SMALL,
                loading = busy,
            )
            FamButton(
                text = stringResource(R.string.candidate_edit),
                onClick = onEdit,
                style = FamButtonStyle.SECONDARY,
                size = FamButtonSize.SMALL,
                enabled = !busy,
            )
            DismissButton(enabled = !busy, onDismiss = onDismiss)
        }
        Text(stringResource(R.string.candidate_ai_label), style = FamTheme.type.caption, color = colors.textTertiary)
    }
}

/** [무시] → 사유(선택) 메뉴 */
@Composable
private fun DismissButton(enabled: Boolean, onDismiss: (DismissReason?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FamButton(
            text = stringResource(R.string.candidate_dismiss),
            onClick = { open = true },
            style = FamButtonStyle.GHOST,
            size = FamButtonSize.SMALL,
            enabled = enabled,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = FamTheme.colors.bgDefault) {
            val choices = listOf(
                DismissReason.ALREADY_REGISTERED to R.string.candidate_dismiss_registered,
                DismissReason.JOKE to R.string.candidate_dismiss_joke,
                DismissReason.WRONG to R.string.candidate_dismiss_wrong,
                null to R.string.candidate_dismiss_plain,
            )
            choices.forEach { (reason, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label), style = FamTheme.type.bodyMedium, color = FamTheme.colors.textPrimary) },
                    onClick = {
                        open = false
                        onDismiss(reason)
                    },
                )
            }
        }
    }
}

/** 근거 인용: 왼쪽 3px 유형 색 + “…” + "엄마 · 오후 2:10 · 원문 보기". 카드 안은 bg/default, 시트 안은 bg/subtle */
@Composable
internal fun Evidence(
    quote: String?,
    meta: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = FamTheme.colors.bgDefault,
) {
    val colors = FamTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(background, RoundedCornerShape(FamDimens.radiusSm))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.candidate_show_original), onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(start = 10.dp, top = 6.dp, end = FamDimens.sm, bottom = 6.dp)) {
            if (quote != null) {
                Text(
                    stringResource(R.string.candidate_quote, quote),
                    style = FamTheme.type.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(meta, style = FamTheme.type.caption, color = colors.textTertiary)
        }
    }
}

/** "추정" 배지 + 무엇을 추정했는지 */
@Composable
internal fun InferredRow(text: String) {
    val colors = FamTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        InferredBadge()
        Text(text, style = FamTheme.type.caption, color = colors.textSecondary)
    }
}

@Composable
internal fun InferredBadge() {
    val colors = FamTheme.colors
    Text(
        stringResource(R.string.candidate_inferred),
        style = FamTheme.type.caption,
        color = colors.textTertiary,
        modifier = Modifier
            .background(colors.bgDefault, CircleShape)
            .border(1.dp, colors.borderDefault, CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Figma Resolved Chip `26:134`: "캘린더에 등록됨 · 엄마 · 보기" + 5분 동안 [되돌리기].
 * 할 일은 아직 상세 화면이 없어 [보기]를 두지 않는다.
 */
@Composable
fun ResolvedChip(
    candidate: Candidate,
    deciderName: String?,
    canUndo: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    val (accent, background) = colors.categoryOf(candidate.type)
    val canOpen = candidate.entityId != null && candidate.entityType != CandidateEntity.TODO
    Row(
        modifier = modifier
            .padding(start = FamDimens.chatIndent)
            .background(background, CircleShape)
            .padding(horizontal = FamDimens.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.xs),
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(
            listOfNotNull(stringResource(candidate.resolvedRes()), deciderName).joinToString(" · ") + if (canOpen) " ·" else "",
            style = FamTheme.type.label,
            color = accent,
        )
        if (canOpen) {
            Text(
                stringResource(R.string.candidate_open),
                style = FamTheme.type.label,
                color = accent,
                modifier = Modifier
                    .heightIn(min = 24.dp)
                    .clickable(role = Role.Button, onClick = onOpen),
            )
        }
        if (canUndo) {
            Text(
                stringResource(R.string.candidate_undo),
                style = FamTheme.type.label,
                color = colors.textTertiary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(start = FamDimens.xs)
                    .heightIn(min = 24.dp)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onUndo),
            )
        }
    }
}

private fun CandidateType.foundRes(): Int = when (this) {
    CandidateType.SCHEDULE -> R.string.candidate_found_schedule
    CandidateType.ANNIVERSARY -> R.string.candidate_found_anniversary
    CandidateType.TODO -> R.string.candidate_found_todo
    CandidateType.PLACE -> R.string.candidate_found_place
}

private fun Candidate.resolvedRes(): Int = when {
    type == CandidateType.TODO -> R.string.candidate_resolved_todo
    type == CandidateType.PLACE && linkedExisting -> R.string.candidate_resolved_place_linked
    type == CandidateType.PLACE -> R.string.candidate_resolved_place
    else -> R.string.candidate_resolved_calendar
}

private val shortDateFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)

/** "11/14(토)" */
internal fun formatShortDate(date: LocalDate): String = shortDateFormatter.format(date)

/** 카드 요약 줄 (유형별). 정하지 못한 값은 "골라 주세요"·"날짜 미정"으로 */
@Composable
private fun candidateMeta(candidate: Candidate, nameOf: (Long?) -> String?): List<String> = when (val f = candidate.fields) {
    is CandidateFields.Schedule -> listOfNotNull(
        listOfNotNull(
            f.date?.let { date ->
                val time = f.startTime?.let { formatClock(it) } ?: stringResource(R.string.schedule_all_day)
                "${formatShortDate(date)} $time"
            } ?: stringResource(R.string.candidate_no_date),
            f.location?.text,
        ).joinToString(" · "),
        peopleText(R.string.candidate_attendees, f.allMembers, f.attendeeIds, f.attendeeChoices, f.unmatchedAttendees, nameOf),
    )
    is CandidateFields.Anniversary -> listOfNotNull(
        if (f.month != null && f.day != null) {
            stringResource(R.string.candidate_anniversary_date, f.month, f.day)
        } else {
            stringResource(R.string.candidate_no_date)
        },
        f.personText?.let { stringResource(R.string.candidate_anniversary_person, it) },
    )
    is CandidateFields.Todo -> listOf(
        listOfNotNull(
            peopleText(R.string.candidate_assignee, f.allMembers, f.assigneeIds, f.assigneeChoices, f.unmatchedAssignees, nameOf),
            f.dueDate?.let { stringResource(R.string.candidate_due, formatShortDate(it)) },
        ).joinToString(" · ").ifEmpty { stringResource(R.string.candidate_no_assignee) },
    )
    is CandidateFields.Place -> listOfNotNull(
        f.chosen?.let { chosen ->
            listOfNotNull(stringResource(chosen.category.labelRes()), regionText(chosen.region1, chosen.region2)).joinToString(" · ")
        } ?: if (f.options.size > 1) {
            stringResource(R.string.candidate_place_options, f.options.size)
        } else {
            stringResource(R.string.candidate_place_not_found)
        },
        f.opinion?.let { opinion ->
            nameOf(f.recommendedBy)?.let { stringResource(R.string.candidate_place_opinion_by, opinion, it) }
                ?: stringResource(R.string.candidate_place_opinion, opinion)
        },
    )
}

/** "참석: 가족 전체" · "참석: 아빠, 엄마" · 같은 역할이 여러 명이면 "참석: 골라 주세요". 못 찾은 표현은 글자로 */
@Composable
private fun peopleText(
    label: Int,
    allMembers: Boolean,
    ids: List<Long>,
    choices: List<List<Long>>,
    unmatched: List<String>,
    nameOf: (Long?) -> String?,
): String? {
    val names = when {
        allMembers -> listOf(stringResource(R.string.candidate_everyone))
        else -> ids.mapNotNull(nameOf) + unmatched
    }
    val value = when {
        choices.isNotEmpty() -> (names + stringResource(R.string.candidate_choose)).joinToString(", ")
        names.isEmpty() -> return null
        else -> names.joinToString(", ")
    }
    return stringResource(label, value)
}

/** 추정한 항목 이름들 ("날짜, 오전·오후") */
@Composable
private fun inferredText(candidate: Candidate): String? {
    val labels = listOf(
        Candidate.INFERRED_DATE to R.string.candidate_inferred_date,
        Candidate.INFERRED_AMPM to R.string.candidate_inferred_ampm,
        Candidate.INFERRED_ATTENDEES to R.string.candidate_inferred_attendees,
        Candidate.INFERRED_ASSIGNEES to R.string.candidate_inferred_assignees,
        Candidate.INFERRED_DUE to R.string.candidate_inferred_due,
    ).filter { candidate.isInferred(it.first) }.map { stringResource(it.second) }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private val clockFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

internal fun formatClock(time: LocalTime): String = clockFormatter.format(time)
