package com.famlog.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.PlaceSummary
import com.famlog.core.model.Reminder
import com.famlog.core.model.RepeatFreq
import com.famlog.feature.chat.formatTime
import com.famlog.feature.chat.SeoulZone
import com.famlog.feature.place.PlaceRow
import com.famlog.feature.place.labelRes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** SCR-21 일정 등록·수정 (SCH-01·03·05). Figma가 없어 후보 수정 시트(02-3)의 필드 패턴을 따른다 */
@Composable
fun ScheduleEditScreen(
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: ScheduleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScheduleEditEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                is ScheduleEditEvent.Saved -> onSaved(event.scheduleId)
                ScheduleEditEvent.Closed -> onClose()
            }
        }
    }
    ScheduleEditContent(
        state = state,
        onClose = onClose,
        onTitleChange = viewModel::onTitleChange,
        onAllDayChange = viewModel::onAllDayChange,
        onStartDateChange = viewModel::onStartDateChange,
        onStartTimeChange = viewModel::onStartTimeChange,
        onEndDateChange = viewModel::onEndDateChange,
        onEndTimeChange = viewModel::onEndTimeChange,
        onToggleParticipant = viewModel::toggleParticipant,
        onRepeatChange = viewModel::onRepeatChange,
        onRepeatUntilChange = viewModel::onRepeatUntilChange,
        onToggleReminder = viewModel::toggleReminder,
        onMemoChange = viewModel::onMemoChange,
        onSave = viewModel::save,
        onOpenPlacePicker = viewModel::openPlacePicker,
        snackbarHostState = snackbarHostState,
    )
    state.placePicker?.let { picker ->
        PlacePickerSheet(
            picker = picker,
            selectedId = state.form.place?.id,
            onDismiss = viewModel::closePlacePicker,
            onPick = viewModel::onPlaceChange,
        )
    }
}

/** 날짜·시간 고르기 창. 한 번에 하나만 연다 */
private sealed interface PickerTarget {
    data object StartDate : PickerTarget
    data object StartTime : PickerTarget
    data object EndDate : PickerTarget
    data object EndTime : PickerTarget
    data object RepeatUntil : PickerTarget
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditContent(
    state: ScheduleEditUiState,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onStartTimeChange: (LocalTime) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onEndTimeChange: (LocalTime) -> Unit,
    onToggleParticipant: (Long) -> Unit,
    onRepeatChange: (RepeatFreq?) -> Unit,
    onRepeatUntilChange: (LocalDate?) -> Unit,
    onToggleReminder: (Reminder) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onOpenPlacePicker: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    val form = state.form
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            FamTopBar(
                onBack = onClose,
                title = stringResource(if (state.isEdit) R.string.schedule_edit_title else R.string.schedule_new_title),
            )
            if (state.loading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brandPrimary)
                }
                return@Column
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
                verticalArrangement = Arrangement.spacedBy(FamDimens.lg),
            ) {
                if (state.isEdit && form.repeatFreq != null) {
                    Text(stringResource(R.string.schedule_recurring_edit_hint), style = FamTheme.type.caption, color = colors.textTertiary)
                }
                FamTextField(
                    value = form.title,
                    onValueChange = onTitleChange,
                    label = stringResource(R.string.schedule_field_title),
                    placeholder = stringResource(R.string.schedule_field_title_hint),
                    errorText = if (state.showErrors && state.titleMissing) stringResource(R.string.schedule_required) else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.schedule_all_day),
                        style = FamTheme.type.bodyLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = form.allDay,
                        onCheckedChange = onAllDayChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.brandPrimary),
                    )
                }

                DateTimeRow(
                    label = stringResource(R.string.schedule_field_start),
                    date = form.startDate,
                    time = form.startTime.takeUnless { form.allDay },
                    onDateClick = { picker = PickerTarget.StartDate },
                    onTimeClick = { picker = PickerTarget.StartTime },
                )
                DateTimeRow(
                    label = stringResource(R.string.schedule_field_end),
                    date = form.endDate,
                    time = form.endTime.takeUnless { form.allDay },
                    onDateClick = { picker = PickerTarget.EndDate },
                    onTimeClick = { picker = PickerTarget.EndTime },
                    error = if (form.endBeforeStart) stringResource(R.string.schedule_end_before_start) else null,
                )

                Field(
                    label = stringResource(R.string.schedule_field_participants),
                    error = if (state.showErrors && state.participantsMissing) stringResource(R.string.schedule_participants_required) else null,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                        state.members.forEach { member ->
                            FamChip(
                                text = member.displayName,
                                selected = member.userId in form.participants,
                                onClick = { onToggleParticipant(member.userId) },
                                dotColor = memberColor(member, state.members),
                                role = Role.Checkbox,
                            )
                        }
                    }
                }

                if (state.conflicts.isNotEmpty()) ConflictNotice(state)

                Field(label = stringResource(R.string.schedule_field_repeat)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                        (listOf(null) + RepeatFreq.entries).forEach { freq ->
                            FamChip(text = freq.label(), selected = form.repeatFreq == freq, onClick = { onRepeatChange(freq) })
                        }
                    }
                }
                if (form.repeatFreq != null) {
                    Field(
                        label = stringResource(R.string.schedule_field_repeat_until),
                        error = if (form.untilBeforeStart) stringResource(R.string.schedule_until_before_start) else null,
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                            FamChip(
                                text = stringResource(R.string.schedule_repeat_forever),
                                selected = form.repeatUntil == null,
                                onClick = { onRepeatUntilChange(null) },
                            )
                            FamChip(
                                text = form.repeatUntil?.let { stringResource(R.string.schedule_repeat_until, formatDay(it, form.startDate)) }
                                    ?: stringResource(R.string.schedule_repeat_until_pick),
                                selected = form.repeatUntil != null,
                                onClick = { picker = PickerTarget.RepeatUntil },
                            )
                        }
                    }
                }

                Field(label = stringResource(R.string.schedule_field_reminders)) {
                    val options = if (form.allDay) listOf(Reminder.PREV_DAY_20) else Reminder.entries
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                        options.forEach { reminder ->
                            FamChip(
                                text = reminder.label(),
                                selected = reminder in form.reminders,
                                onClick = { onToggleReminder(reminder) },
                                role = Role.Checkbox,
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            when {
                                form.reminders.isEmpty() -> R.string.schedule_reminder_none
                                form.allDay -> R.string.schedule_reminder_all_day_hint
                                else -> R.string.schedule_reminder_hint
                            },
                        ),
                        style = FamTheme.type.caption,
                        color = colors.textTertiary,
                    )
                }

                Field(label = stringResource(R.string.place_field)) {
                    PickerBox(
                        text = form.place?.name ?: stringResource(R.string.place_none),
                        onClick = onOpenPlacePicker,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FamTextField(
                    value = form.memo,
                    onValueChange = onMemoChange,
                    label = stringResource(R.string.schedule_field_memo),
                    singleLine = false,
                    minLines = 3,
                )
            }
            FamButton(
                text = stringResource(R.string.schedule_save),
                onClick = onSave,
                loading = state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm)
                    .navigationBarsPadding(),
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }

    when (val target = picker) {
        PickerTarget.StartDate -> DatePickerDialogFor(form.startDate, { picker = null }) { onStartDateChange(it) }
        PickerTarget.EndDate -> DatePickerDialogFor(form.endDate, { picker = null }) { onEndDateChange(it) }
        PickerTarget.RepeatUntil -> DatePickerDialogFor(form.repeatUntil ?: form.startDate, { picker = null }) {
            onRepeatUntilChange(it)
        }
        PickerTarget.StartTime -> TimePickerDialogFor(form.startTime, { picker = null }) { onStartTimeChange(it) }
        PickerTarget.EndTime -> TimePickerDialogFor(form.endTime, { picker = null }) { onEndTimeChange(it) }
        null -> Unit
    }
}

/** 일정에 연결할 우리 그룹 장소 고르기. 맨 위 "장소 빼기"로 연결을 비운다 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePickerSheet(
    picker: PlacePicker,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onPick: (PlaceSummary?) -> Unit,
) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        Text(
            stringResource(R.string.place_pick_title),
            style = FamTheme.type.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = FamDimens.lg, vertical = FamDimens.sm).semantics { heading() },
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (selectedId != null) {
                item(key = "clear") {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.padding(horizontal = FamDimens.sm)) {
                        Text(stringResource(R.string.place_pick_clear), style = FamTheme.type.label, color = colors.statusDanger)
                    }
                }
            }
            when {
                picker.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(FamDimens.xxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.brandPrimary)
                    }
                }
                picker.failed -> item(key = "failed") {
                    Text(
                        stringResource(R.string.place_load_failed),
                        style = FamTheme.type.bodyMedium,
                        color = colors.statusDanger,
                        modifier = Modifier.padding(FamDimens.lg),
                    )
                }
                picker.places.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(R.string.place_pick_empty),
                        style = FamTheme.type.bodyMedium,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(FamDimens.lg),
                    )
                }
            }
            items(picker.places, key = { it.id }) { place ->
                PlaceRow(
                    name = place.name,
                    subtitle = listOfNotNull(stringResource(place.category.labelRes()), place.address).joinToString(" · "),
                    rating = null,
                    onClick = { onPick(place) },
                    modifier = if (place.id == selectedId) Modifier.background(colors.brandSubtle) else Modifier,
                )
            }
        }
    }
}

/** 라벨(Caption secondary) + 내용 + 오류(status/danger) */
@Composable
private fun Field(label: String, error: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        Text(label, style = FamTheme.type.caption, color = FamTheme.colors.textSecondary)
        content()
        if (error != null) Text(error, style = FamTheme.type.caption, color = FamTheme.colors.statusDanger)
    }
}

/** 날짜·시간 상자 (radius 12, 1px border). 종일이면 시간 상자는 없다 */
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    error: String? = null,
) {
    Field(label = label, error = error) {
        Row(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
            PickerBox(text = formatDay(date, LocalDate.now(SeoulZone)), onClick = onDateClick, modifier = Modifier.weight(1f))
            if (time != null) {
                PickerBox(
                    text = formatTime(date.atTime(time).atZone(SeoulZone).toInstant()),
                    onClick = onTimeClick,
                    modifier = Modifier.weight(0.7f),
                )
            }
        }
    }
}

@Composable
private fun PickerBox(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FamDimens.radiusMd),
        border = BorderStroke(1.dp, FamTheme.colors.borderStrong),
        color = FamTheme.colors.bgDefault,
        modifier = modifier.heightIn(min = FamDimens.minTouchTarget),
    ) {
        Box(Modifier.padding(horizontal = FamDimens.md, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, style = FamTheme.type.bodyLarge, color = FamTheme.colors.textPrimary)
        }
    }
}

/** "아빠는 이 시간에 '치과' 일정이 있어요" (SCH-05). 막지 않고 알리기만 한다 */
@Composable
private fun ConflictNotice(state: ScheduleEditUiState) {
    val colors = FamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.categoryScheduleBg, RoundedCornerShape(FamDimens.radiusMd))
            .padding(FamDimens.md),
        verticalArrangement = Arrangement.spacedBy(FamDimens.xs),
    ) {
        state.conflicts.forEach { conflict ->
            val names = conflict.userIds.mapNotNull(state::nameOf).joinToString("·")
            if (names.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_conflict, names, topicParticle(names), conflict.title),
                    style = FamTheme.type.bodyMedium,
                    color = colors.categorySchedule,
                )
            }
        }
    }
}

/** Material 3 DatePicker는 UTC 자정 millis로 주고받는다 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerDialogFor(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.schedule_ok), color = FamTheme.colors.brandPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_cancel), color = FamTheme.colors.textSecondary) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogFor(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(pickerState.hour, pickerState.minute))
                onDismiss()
            }) { Text(stringResource(R.string.schedule_ok), color = FamTheme.colors.brandPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_cancel), color = FamTheme.colors.textSecondary) }
        },
        text = { TimePicker(state = pickerState) },
        containerColor = FamTheme.colors.bgDefault,
    )
}
