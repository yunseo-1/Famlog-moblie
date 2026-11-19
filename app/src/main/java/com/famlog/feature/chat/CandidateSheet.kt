package com.famlog.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonSize
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.designsystem.theme.categoryOf
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateEvidence
import com.famlog.core.model.CandidateType
import com.famlog.core.model.Member
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceOption
import com.famlog.feature.calendar.DatePickerDialogFor
import com.famlog.feature.calendar.TimePickerDialogFor
import com.famlog.feature.calendar.formatDay
import com.famlog.feature.calendar.memberColor
import com.famlog.feature.place.CategoryPicker
import com.famlog.feature.place.labelRes
import com.famlog.feature.place.regionText
import java.time.LocalDate
import java.time.LocalTime

/** 수정 시트의 입력 동작 */
data class CandidateSheetActions(
    val onDismiss: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onShowEvidence: (CandidateEvidence) -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onClearDueDate: () -> Unit = {},
    val onAllDayChange: (Boolean) -> Unit = {},
    val onStartTimeChange: (LocalTime) -> Unit = {},
    val onEndTimeChange: (LocalTime) -> Unit = {},
    val onToggleParticipant: (Long) -> Unit = {},
    val onLocationChange: (String) -> Unit = {},
    val onAssigneeChange: (Long) -> Unit = {},
    val onPlaceSelect: (PlaceOption) -> Unit = {},
    val onCategoryChange: (PlaceCategory) -> Unit = {},
    val onReviewChange: (String) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
)

/** SCR-13 등록 후보 수정 바텀시트 (Figma 02-3) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidateSheetDialog(sheet: CandidateSheet, members: List<Member>, today: LocalDate, actions: CandidateSheetActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = FamTheme.colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = FamTheme.colors.surfacePlaceholder) },
    ) {
        CandidateSheetContent(sheet, members, today, actions)
    }
}

@Composable
fun CandidateSheetContent(sheet: CandidateSheet, members: List<Member>, today: LocalDate, actions: CandidateSheetActions) {
    val colors = FamTheme.colors
    val candidate = sheet.candidate
    var picker by remember { mutableStateOf<SheetPicker?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = FamDimens.lg, end = FamDimens.lg, top = FamDimens.md, bottom = FamDimens.xl)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            Text(
                stringResource(candidate.type.sheetTitleRes()),
                style = FamTheme.type.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.candidate_sheet_guide), style = FamTheme.type.caption, color = colors.textTertiary)
        }
        when (val form = sheet.form) {
            null -> Box(Modifier.fillMaxWidth().padding(FamDimens.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brandPrimary)
            }
            is CandidateForm.Schedule -> ScheduleFields(form, sheet, members, actions) { picker = it }
            is CandidateForm.Anniversary -> AnniversaryFields(form, sheet, actions) { picker = it }
            is CandidateForm.Todo -> TodoFields(form, sheet, members, actions) { picker = it }
            is CandidateForm.Place -> PlaceFields(form, sheet, actions)
        }
        sheet.evidence.filter { it.text != null }.forEach { evidence ->
            Evidence(
                quote = evidence.text.orEmpty(),
                meta = stringResource(
                    R.string.candidate_sheet_evidence_meta,
                    members.firstOrNull { it.userId == evidence.senderId }?.displayName ?: stringResource(R.string.chat_unknown_member),
                    formatShortDate(evidence.sentAt.atZone(SeoulZone).toLocalDate()),
                    formatTime(evidence.sentAt),
                ),
                accent = colors.categoryOf(candidate.type).first,
                onClick = { actions.onShowEvidence(evidence) },
                background = colors.bgSubtle,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
            FamButton(
                text = stringResource(R.string.candidate_cancel),
                onClick = actions.onDismiss,
                style = FamButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            FamButton(
                text = stringResource(R.string.candidate_approve),
                onClick = actions.onSubmit,
                enabled = sheet.canSubmit,
                loading = sheet.saving,
                modifier = Modifier.weight(1f),
            )
        }
    }

    when (val target = picker) {
        null -> Unit
        is SheetPicker.Date -> DatePickerDialogFor(target.initial ?: today, onDismiss = { picker = null }, onPick = actions.onDateChange)
        is SheetPicker.Start -> TimePickerDialogFor(target.initial, onDismiss = { picker = null }, onPick = actions.onStartTimeChange)
        is SheetPicker.End -> TimePickerDialogFor(target.initial, onDismiss = { picker = null }, onPick = actions.onEndTimeChange)
    }
}

private sealed interface SheetPicker {
    data class Date(val initial: LocalDate?) : SheetPicker
    data class Start(val initial: LocalTime) : SheetPicker
    data class End(val initial: LocalTime) : SheetPicker
}

@Composable
private fun ScheduleFields(
    form: CandidateForm.Schedule,
    sheet: CandidateSheet,
    members: List<Member>,
    actions: CandidateSheetActions,
    openPicker: (SheetPicker) -> Unit,
) {
    val colors = FamTheme.colors
    val candidate = sheet.candidate
    TitleField(form.title, sheet, actions)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.schedule_all_day), style = FamTheme.type.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = form.allDay,
            onCheckedChange = actions.onAllDayChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.brandPrimary),
        )
    }
    SheetField(
        label = stringResource(R.string.candidate_field_date),
        attention = candidate.isInferred(Candidate.INFERRED_DATE) || CandidateForm.FIELD_DATE in sheet.serverMissing,
        error = CandidateForm.FIELD_DATE in sheet.errors,
    ) {
        PickerBox(
            text = form.date?.let { formatDay(it, LocalDate.now(SeoulZone)) } ?: stringResource(R.string.candidate_pick_date),
            highlighted = candidate.isInferred(Candidate.INFERRED_DATE),
            onClick = { openPicker(SheetPicker.Date(form.date)) },
        )
    }
    if (!form.allDay) {
        SheetField(
            label = stringResource(R.string.candidate_field_time),
            attention = candidate.isInferred(Candidate.INFERRED_AMPM),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm), verticalAlignment = Alignment.CenterVertically) {
                PickerBox(
                    text = formatClock(form.startTime),
                    highlighted = candidate.isInferred(Candidate.INFERRED_AMPM),
                    onClick = { openPicker(SheetPicker.Start(form.startTime)) },
                    modifier = Modifier.weight(1f),
                )
                Text("~", style = FamTheme.type.bodyLarge, color = colors.textTertiary)
                PickerBox(
                    text = formatClock(form.endTime),
                    onClick = { openPicker(SheetPicker.End(form.endTime)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    SheetField(
        label = stringResource(R.string.candidate_field_attendees),
        attention = candidate.isInferred(Candidate.INFERRED_ATTENDEES) || CandidateForm.FIELD_ATTENDEES in sheet.serverMissing,
        error = CandidateForm.FIELD_ATTENDEES in sheet.errors,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm), verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            members.forEach { member ->
                FamChip(
                    text = member.displayName,
                    selected = member.userId in form.participantIds,
                    onClick = { actions.onToggleParticipant(member.userId) },
                    dotColor = memberColor(member, members),
                    role = Role.Checkbox,
                )
            }
        }
    }
    FamTextField(
        value = form.locationText,
        onValueChange = actions.onLocationChange,
        label = stringResource(R.string.candidate_field_location),
        placeholder = stringResource(R.string.candidate_field_location_hint),
        supportingText = if (form.placeId != null && form.locationText.trim() == form.originalLocation.trim()) {
            stringResource(R.string.candidate_location_linked)
        } else {
            null
        },
    )
}

@Composable
private fun AnniversaryFields(
    form: CandidateForm.Anniversary,
    sheet: CandidateSheet,
    actions: CandidateSheetActions,
    openPicker: (SheetPicker) -> Unit,
) {
    TitleField(form.title, sheet, actions)
    SheetField(
        label = stringResource(R.string.candidate_field_anniversary),
        attention = sheet.candidate.isInferred(Candidate.INFERRED_DATE) || CandidateForm.FIELD_DATE in sheet.serverMissing,
        error = CandidateForm.FIELD_DATE in sheet.errors,
    ) {
        PickerBox(
            text = form.date?.let { formatDay(it, LocalDate.now(SeoulZone)) } ?: stringResource(R.string.candidate_pick_date),
            highlighted = sheet.candidate.isInferred(Candidate.INFERRED_DATE),
            onClick = { openPicker(SheetPicker.Date(form.date)) },
        )
    }
    Text(stringResource(R.string.candidate_anniversary_note), style = FamTheme.type.caption, color = FamTheme.colors.textTertiary)
}

@Composable
private fun TodoFields(
    form: CandidateForm.Todo,
    sheet: CandidateSheet,
    members: List<Member>,
    actions: CandidateSheetActions,
    openPicker: (SheetPicker) -> Unit,
) {
    val candidate = sheet.candidate
    TitleField(form.title, sheet, actions)
    SheetField(
        label = stringResource(R.string.candidate_field_assignee),
        attention = candidate.isInferred(Candidate.INFERRED_ASSIGNEES) || CandidateForm.FIELD_ASSIGNEES in sheet.serverMissing,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm), verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            members.forEach { member ->
                FamChip(
                    text = member.displayName,
                    selected = member.userId == form.assigneeId,
                    onClick = { actions.onAssigneeChange(member.userId) },
                    dotColor = memberColor(member, members),
                )
            }
        }
    }
    SheetField(label = stringResource(R.string.candidate_field_due), attention = candidate.isInferred(Candidate.INFERRED_DUE)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PickerBox(
                text = form.dueDate?.let { formatDay(it, LocalDate.now(SeoulZone)) } ?: stringResource(R.string.candidate_no_due),
                highlighted = candidate.isInferred(Candidate.INFERRED_DUE),
                onClick = { openPicker(SheetPicker.Date(form.dueDate)) },
                modifier = Modifier.weight(1f),
            )
            if (form.dueDate != null) {
                TextButton(onClick = actions.onClearDueDate) {
                    Text(stringResource(R.string.candidate_clear), style = FamTheme.type.label, color = FamTheme.colors.textSecondary)
                }
            }
        }
    }
}

/** 서버가 찾은 곳(최대 3) 중 고르기 + 다시 검색 + 카테고리 + 한줄평 */
@Composable
private fun PlaceFields(form: CandidateForm.Place, sheet: CandidateSheet, actions: CandidateSheetActions) {
    val colors = FamTheme.colors
    SheetField(
        label = stringResource(R.string.candidate_field_place),
        attention = CandidateForm.FIELD_PLACE in sheet.serverMissing,
        error = CandidateForm.FIELD_PLACE in sheet.errors,
    ) {
        Column {
            if (form.options.isEmpty()) {
                Text(stringResource(R.string.candidate_place_not_found), style = FamTheme.type.bodyMedium, color = colors.textTertiary)
            }
            form.options.forEach { option ->
                PlaceOptionRow(option, selected = option.kakaoPlaceId == form.selected?.kakaoPlaceId, onClick = { actions.onPlaceSelect(option) })
            }
        }
    }
    FamTextField(
        value = form.query,
        onValueChange = actions.onQueryChange,
        label = stringResource(R.string.candidate_place_search),
        placeholder = stringResource(R.string.candidate_place_search_hint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
    )
    FamButton(
        text = stringResource(R.string.candidate_place_search_button),
        onClick = actions.onSearch,
        style = FamButtonStyle.SECONDARY,
        size = FamButtonSize.SMALL,
        loading = form.searching,
        enabled = form.query.isNotBlank(),
    )
    if (form.searched && form.results.isEmpty()) {
        Text(stringResource(R.string.candidate_place_search_empty), style = FamTheme.type.bodyMedium, color = colors.textTertiary)
    }
    form.results.forEach { option ->
        PlaceOptionRow(option, selected = false, onClick = { actions.onPlaceSelect(option) })
    }
    SheetField(label = stringResource(R.string.candidate_field_category)) {
        CategoryPicker(selected = form.category, onSelect = actions.onCategoryChange)
    }
    FamTextField(
        value = form.review,
        onValueChange = actions.onReviewChange,
        label = stringResource(R.string.candidate_field_review),
        placeholder = stringResource(R.string.candidate_field_review_hint),
    )
}

@Composable
private fun PlaceOptionRow(option: PlaceOption, selected: Boolean, onClick: () -> Unit) {
    val colors = FamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.minTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = FamDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.brandPrimary))
        Column(Modifier.padding(start = FamDimens.sm)) {
            Text(option.name, style = FamTheme.type.bodyMediumStrong, color = colors.textPrimary)
            Text(
                listOfNotNull(stringResource(option.category.labelRes()), option.address ?: regionText(option.region1, option.region2))
                    .joinToString(" · "),
                style = FamTheme.type.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun TitleField(title: String, sheet: CandidateSheet, actions: CandidateSheetActions) {
    FamTextField(
        value = title,
        onValueChange = actions.onTitleChange,
        label = stringResource(R.string.candidate_field_title),
        errorText = if (CandidateForm.FIELD_TITLE in sheet.errors) stringResource(R.string.candidate_required) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

/** 라벨 + (AI 추정·확인 필요 배지) + 내용 + "입력해 주세요" */
@Composable
private fun SheetField(label: String, attention: Boolean = false, error: Boolean = false, content: @Composable () -> Unit) {
    val colors = FamTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            Text(label, style = FamTheme.type.caption, color = colors.textSecondary)
            if (attention) {
                Text(
                    stringResource(R.string.candidate_check_needed),
                    style = FamTheme.type.caption,
                    color = colors.brandPrimary,
                    modifier = Modifier
                        .background(colors.brandSubtle, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        content()
        if (error) Text(stringResource(R.string.candidate_required), style = FamTheme.type.caption, color = colors.statusDanger)
    }
}

/** 입력 상자 (radius 12, 1px border). AI 추정 값이면 테두리를 brand로 */
@Composable
private fun PickerBox(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val colors = FamTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FamDimens.radiusMd),
        border = BorderStroke(1.dp, if (highlighted) colors.brandPrimary else colors.borderStrong),
        color = colors.bgDefault,
        modifier = modifier.fillMaxWidth().heightIn(min = FamDimens.minTouchTarget),
    ) {
        Box(Modifier.padding(horizontal = FamDimens.md, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, style = FamTheme.type.bodyLarge, color = colors.textPrimary)
        }
    }
}

private fun CandidateType.sheetTitleRes(): Int = when (this) {
    CandidateType.SCHEDULE -> R.string.candidate_sheet_schedule
    CandidateType.ANNIVERSARY -> R.string.candidate_sheet_anniversary
    CandidateType.TODO -> R.string.candidate_sheet_todo
    CandidateType.PLACE -> R.string.candidate_sheet_place
}
