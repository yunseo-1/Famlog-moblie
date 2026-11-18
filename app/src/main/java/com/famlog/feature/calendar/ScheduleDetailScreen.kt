package com.famlog.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.Schedule
import com.famlog.feature.chat.SeoulZone
import java.time.LocalDate

/** SCR-21 일정 상세 (SCH-01). 수정·삭제는 등록한 사람 또는 관리자 이상에게만 보인다 */
@Composable
fun ScheduleDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScheduleDetailEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                ScheduleDetailEvent.Closed -> onBack()
            }
        }
    }
    ScheduleDetailContent(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(viewModel.scheduleId) },
        onDelete = viewModel::delete,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleDetailContent(
    state: ScheduleDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack) {
                if (state.canEdit) {
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.schedule_edit), style = FamTheme.type.label, color = colors.brandPrimary)
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = !state.deleting) {
                        Text(stringResource(R.string.schedule_delete), style = FamTheme.type.label, color = colors.statusDanger)
                    }
                }
            }
            val schedule = state.schedule
            if (schedule == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brandPrimary)
                }
            } else {
                ScheduleBody(schedule, state)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.schedule_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.schedule?.rrule != null) R.string.schedule_delete_body_recurring else R.string.schedule_delete_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.schedule_delete), color = colors.statusDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.schedule_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.bgDefault,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleBody(schedule: Schedule, state: ScheduleDetailUiState) {
    val colors = FamTheme.colors
    val today = LocalDate.now(SeoulZone)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
        verticalArrangement = Arrangement.spacedBy(FamDimens.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
            Text(
                schedule.title,
                style = FamTheme.type.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            val dayText = if (schedule.startDate == schedule.endDate || !schedule.allDay) {
                formatDay(schedule.startDate, today)
            } else {
                null
            }
            val timeText = formatTimeRange(
                schedule.allDay, schedule.startsAt, schedule.endsAt, schedule.startDate, schedule.endDate,
                stringResource(R.string.schedule_all_day),
            )
            Text(
                listOfNotNull(dayText, timeText).joinToString(" "),
                style = FamTheme.type.bodyLarge,
                color = colors.textSecondary,
            )
            schedule.repeat?.let { repeat ->
                Text(repeat.describe(today), style = FamTheme.type.bodyMedium, color = colors.textSecondary)
            }
        }

        DetailField(stringResource(R.string.schedule_field_participants)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
                verticalArrangement = Arrangement.spacedBy(FamDimens.xs),
            ) {
                schedule.participantIds.forEach { id ->
                    val member = state.members.firstOrNull { it.userId == id }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (member != null) {
                            Box(Modifier.size(8.dp).background(memberColor(member, state.members), CircleShape))
                        }
                        Text(member?.displayName ?: "-", style = FamTheme.type.bodyMedium, color = colors.textPrimary)
                    }
                }
            }
        }

        DetailField(stringResource(R.string.schedule_field_reminders)) {
            val text = if (schedule.reminders.isEmpty()) {
                stringResource(R.string.schedule_reminder_none)
            } else {
                schedule.reminders.map { it.label() }.joinToString(", ")
            }
            Text(text, style = FamTheme.type.bodyMedium, color = colors.textPrimary)
        }

        schedule.memo?.takeIf { it.isNotBlank() }?.let { memo ->
            DetailField(stringResource(R.string.schedule_field_memo)) {
                Text(memo, style = FamTheme.type.bodyMedium, color = colors.textPrimary)
            }
        }

        state.nameOf(schedule.createdBy)?.let { name ->
            Text(stringResource(R.string.schedule_created_by, name), style = FamTheme.type.caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun DetailField(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        Text(label, style = FamTheme.type.caption, color = FamTheme.colors.textSecondary)
        content()
    }
}
