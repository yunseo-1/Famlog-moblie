package com.famlog.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.CandidateType
import com.famlog.core.model.ItemSource
import com.famlog.core.model.Todo
import com.famlog.feature.chat.ChatSourceRow
import com.famlog.feature.chat.ChatSourceTag
import java.time.LocalDate

/** 캘린더 탭 [할 일] (TODO-01) */
@Composable
fun TodoTab(
    onOpenSource: (groupId: Long, source: ItemSource) -> Unit = { _, _ -> },
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TodoEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
            }
        }
    }
    TodoContent(
        state = state,
        onSelectAll = viewModel::selectAll,
        onToggleMember = viewModel::toggleMember,
        onToggleDone = viewModel::toggleDone,
        onOpen = viewModel::openEdit,
        onAdd = viewModel::openNew,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::refresh,
        snackbarHostState = snackbarHostState,
    )
    state.sheet?.let { sheet ->
        TodoSheetDialog(
            sheet = sheet,
            state = state,
            onDismiss = viewModel::closeSheet,
            onTitleChange = viewModel::onSheetTitleChange,
            onAssigneeChange = viewModel::onSheetAssigneeChange,
            onDueDateChange = viewModel::onSheetDueDateChange,
            onSave = viewModel::saveSheet,
            onDelete = viewModel::deleteSheetTodo,
            onOpenSource = { todo, source ->
                viewModel.closeSheet()
                onOpenSource(todo.groupId, source)
            },
        )
    }
}

@Composable
fun TodoContent(
    state: TodoUiState,
    onSelectAll: () -> Unit,
    onToggleMember: (Long) -> Unit,
    onToggleDone: (Todo) -> Unit,
    onOpen: (Todo) -> Unit,
    onAdd: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor) { if (nearEnd && state.nextCursor != null) onLoadMore() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = FamDimens.fabSize + FamDimens.xxxl),
        ) {
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = FamDimens.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
                ) {
                    item(key = "all") {
                        FamChip(stringResource(R.string.calendar_filter_all), state.selectedMembers.isEmpty(), onSelectAll)
                    }
                    items(state.members, key = { it.userId }) { member ->
                        FamChip(
                            text = member.displayName,
                            selected = member.userId in state.selectedMembers,
                            onClick = { onToggleMember(member.userId) },
                            dotColor = memberColor(member, state.members),
                            role = Role.Checkbox,
                        )
                    }
                }
            }
            if (state.loadFailed) {
                item(key = "failed") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.todo_load_failed),
                            style = FamTheme.type.bodyMedium,
                            color = FamTheme.colors.statusDanger,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.calendar_retry), style = FamTheme.type.label, color = FamTheme.colors.brandPrimary)
                        }
                    }
                }
            }
            item(key = "open-header") { SectionHeader(stringResource(R.string.todo_section_open, state.open.size)) }
            if (state.open.isEmpty() && !state.loading) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.todo_empty),
                        style = FamTheme.type.bodyMedium,
                        color = FamTheme.colors.textTertiary,
                        modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
                    )
                }
            }
            items(state.open, key = { "todo:${it.id}" }) { todo -> TodoRow(todo, state, onToggleDone, onOpen) }
            if (state.done.isNotEmpty()) {
                item(key = "done-header") { SectionHeader(stringResource(R.string.todo_section_done)) }
                items(state.done, key = { "todo:${it.id}" }) { todo -> TodoRow(todo, state, onToggleDone, onOpen) }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = FamTheme.colors.brandPrimary,
            contentColor = FamTheme.colors.textOnBrand,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(FamDimens.lg),
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.todo_add))
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = FamDimens.fabSize + FamDimens.lg))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = FamTheme.type.bodyMediumStrong,
        color = FamTheme.colors.textPrimary,
        modifier = Modifier
            .padding(horizontal = FamDimens.screenPadding)
            .padding(top = FamDimens.lg, bottom = FamDimens.xs)
            .semantics { heading() },
    )
}

/** 체크 원(할 일 색) + 제목(완료면 취소선) + 담당자 점·이름 · 기한. 행을 누르면 수정 시트 */
@Composable
private fun TodoRow(todo: Todo, state: TodoUiState, onToggleDone: (Todo) -> Unit, onOpen: (Todo) -> Unit) {
    val colors = FamTheme.colors
    val assignee = state.member(todo.assigneeId)
    val overdue = !todo.done && todo.dueDate?.isBefore(state.today) == true
    val toggleLabel = stringResource(if (todo.done) R.string.todo_mark_open else R.string.todo_mark_done, todo.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(todo) }
            .padding(horizontal = FamDimens.sm, vertical = FamDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(FamDimens.minTouchTarget)
                .toggleable(value = todo.done, role = Role.Checkbox, onValueChange = { onToggleDone(todo) })
                .semantics { contentDescription = toggleLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(if (todo.done) colors.categoryTodo else colors.bgDefault, CircleShape)
                    .border(BorderStroke(2.dp, if (todo.done) colors.categoryTodo else colors.borderStrong), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (todo.done) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.textOnBrand, modifier = Modifier.size(14.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = FamDimens.xs)) {
            Text(
                text = todo.title,
                style = FamTheme.type.bodyMediumStrong,
                color = if (todo.done) colors.textTertiary else colors.textPrimary,
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 후보 승인으로 생긴 할 일 (ARC-06). 원문 보기는 수정 시트에서
                if (todo.source != null) ChatSourceTag(CandidateType.TODO)
                if (assignee != null) {
                    Box(Modifier.size(6.dp).background(memberColor(assignee, state.members), CircleShape))
                }
                Text(
                    text = assignee?.displayName ?: stringResource(R.string.todo_unassigned),
                    style = FamTheme.type.caption,
                    color = colors.textSecondary,
                )
                todo.dueDate?.let { due ->
                    Text(
                        text = "· " + stringResource(if (overdue) R.string.todo_overdue else R.string.todo_due, formatDay(due, state.today)),
                        style = FamTheme.type.caption,
                        color = if (overdue) colors.statusDanger else colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** 할 일 추가·수정 바텀시트. 권한이 없으면 읽기만 (완료는 목록에서 누구나) */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TodoSheetDialog(
    sheet: TodoSheet,
    state: TodoUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAssigneeChange: (Long) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onOpenSource: (Todo, ItemSource) -> Unit,
) {
    val colors = FamTheme.colors
    val editable = sheet.todo == null || state.canEdit(sheet.todo)
    var pickDate by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = FamDimens.lg, end = FamDimens.lg, bottom = FamDimens.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FamDimens.md),
        ) {
            Text(
                stringResource(if (sheet.todo == null) R.string.todo_add else R.string.todo_edit_title),
                style = FamTheme.type.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (!editable) {
                Text(stringResource(R.string.todo_view_only), style = FamTheme.type.caption, color = colors.textTertiary)
            }
            val todo = sheet.todo
            todo?.source?.let { source -> ChatSourceRow(CandidateType.TODO, onOpen = { onOpenSource(todo, source) }) }
            FamTextField(
                value = sheet.title,
                onValueChange = onTitleChange,
                label = stringResource(R.string.todo_field_title),
                placeholder = stringResource(R.string.todo_field_title_hint),
                errorText = if (sheet.showErrors && sheet.titleMissing) stringResource(R.string.schedule_required) else null,
                enabled = editable,
            )
            Text(stringResource(R.string.todo_field_assignee), style = FamTheme.type.caption, color = colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                state.members.forEach { member ->
                    FamChip(
                        text = member.displayName,
                        selected = sheet.assigneeId == member.userId,
                        onClick = { if (editable) onAssigneeChange(member.userId) },
                        dotColor = memberColor(member, state.members),
                    )
                }
            }
            Text(stringResource(R.string.todo_field_due), style = FamTheme.type.caption, color = colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                FamChip(
                    text = stringResource(R.string.todo_due_none),
                    selected = sheet.dueDate == null,
                    onClick = { if (editable) onDueDateChange(null) },
                )
                FamChip(
                    text = sheet.dueDate?.let { stringResource(R.string.todo_due, formatDay(it, state.today)) }
                        ?: stringResource(R.string.todo_due_pick),
                    selected = sheet.dueDate != null,
                    onClick = { if (editable) pickDate = true },
                )
            }
            Text(stringResource(R.string.todo_announce_hint), style = FamTheme.type.caption, color = colors.textTertiary)
            if (editable) {
                FamButton(
                    text = stringResource(R.string.todo_save),
                    onClick = onSave,
                    loading = sheet.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sheet.todo != null) {
                    FamButton(
                        text = stringResource(R.string.todo_delete),
                        onClick = onDelete,
                        enabled = !sheet.saving,
                        style = FamButtonStyle.GHOST,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (pickDate) {
        DatePickerDialogFor(sheet.dueDate ?: state.today, { pickDate = false }) { onDueDateChange(it) }
    }
}

private const val LOAD_MORE_THRESHOLD = 5
