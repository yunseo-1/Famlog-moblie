package com.famlog.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.TodoRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Todo
import com.famlog.core.model.TodoEdit
import com.famlog.core.model.errorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** 할 일 추가·수정 시트 입력. [todo]가 null이면 새로 추가 */
data class TodoSheet(
    val todo: Todo? = null,
    val title: String = "",
    val assigneeId: Long? = null,
    val dueDate: LocalDate? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
) {
    val titleMissing: Boolean get() = title.isBlank()
}

data class TodoUiState(
    val groupId: Long? = null,
    val members: List<Member> = emptyList(),
    val myUserId: Long? = null,
    val myRole: GroupRole = GroupRole.MEMBER,
    val todos: List<Todo> = emptyList(),
    /** 비었으면 [전체]. 고른 사람이 담당자인 할 일만 */
    val selectedMembers: Set<Long> = emptySet(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loadFailed: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val sheet: TodoSheet? = null,
) {
    private val visible: List<Todo>
        get() = if (selectedMembers.isEmpty()) todos else todos.filter { it.assigneeId in selectedMembers }

    /** 서버 순서를 지킨다: 안 한 일은 기한 이른 순(없음은 뒤), 완료한 일은 최근 완료 순 */
    val open: List<Todo> get() = visible.filter { !it.done }
    val done: List<Todo> get() = visible.filter { it.done }

    /** 제목·담당자·기한 고치기와 삭제는 등록한 사람 또는 관리자 이상 (완료는 누구나) */
    fun canEdit(todo: Todo): Boolean = todo.createdBy == myUserId || myRole != GroupRole.MEMBER

    fun member(userId: Long?): Member? = userId?.let { id -> members.firstOrNull { it.userId == id } }
}

sealed interface TodoEvent {
    data class Message(val text: UiText) : TodoEvent
}

/** 캘린더 탭 [할 일] (TODO-01). 현재 가족 그룹의 할 일만 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(TodoUiState(today = LocalDate.now(clock)))
    private var loadJob: Job? = null

    val uiState: StateFlow<TodoUiState> = combine(local, groupRepository.details) { state, details ->
        val detail = state.groupId?.let { details[it] }
        state.copy(members = detail?.members.orEmpty(), myRole = detail?.myRole ?: GroupRole.MEMBER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val _events = Channel<TodoEvent>(Channel.BUFFERED)
    val events: Flow<TodoEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { local.update { it.copy(myUserId = sessionStore.userId()) } }
        viewModelScope.launch {
            groupRepository.currentGroupId.filterNotNull().distinctUntilChanged().collect { groupId ->
                local.update { it.copy(groupId = groupId, todos = emptyList(), selectedMembers = emptySet(), sheet = null) }
                launch { if (groupRepository.details.value[groupId] == null) groupRepository.detail(groupId) }
                load()
            }
        }
    }

    fun refresh() {
        if (local.value.groupId != null) load()
    }

    fun selectAll() = local.update { it.copy(selectedMembers = emptySet()) }

    fun toggleMember(userId: Long) = local.update { state ->
        val next = if (userId in state.selectedMembers) state.selectedMembers - userId else state.selectedMembers + userId
        state.copy(selectedMembers = next)
    }

    /** 목록 끝에 가까워지면 다음 페이지 */
    fun loadMore() {
        val state = local.value
        val groupId = state.groupId ?: return
        val cursor = state.nextCursor ?: return
        if (state.loadingMore || state.loading) return
        local.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = todoRepository.page(groupId, cursor)
            local.update { current ->
                if (result is ApiResult.Success) {
                    current.copy(
                        todos = current.todos + result.value.items.filter { new -> current.todos.none { it.id == new.id } },
                        nextCursor = result.value.nextCursor,
                        loadingMore = false,
                    )
                } else {
                    current.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * 완료 체크·되돌리기. 바로 화면에 반영하고 실패하면 되돌린다.
     * 새로 완료하면 채팅방에 한 줄 알린다 (TODO-01 "완료 시 채팅방에 한 줄 알림", 기본 켬 — 사용자 결정).
     */
    fun toggleDone(todo: Todo) {
        val done = !todo.done
        replace(todo.copy(done = done))
        viewModelScope.launch {
            val result = todoRepository.setDone(todo, done, announce = true)
            when {
                result is ApiResult.Success -> {
                    // 완료·되돌리기는 목록 순서가 바뀌므로 서버 순서로 다시 받는다
                    replace(result.value)
                    load()
                }
                else -> {
                    replace(todo)
                    onFailure(result)
                }
            }
        }
    }

    fun openNew() = local.update { it.copy(sheet = TodoSheet(assigneeId = it.myUserId)) }

    fun openEdit(todo: Todo) = local.update {
        it.copy(sheet = TodoSheet(todo = todo, title = todo.title, assigneeId = todo.assigneeId, dueDate = todo.dueDate))
    }

    fun closeSheet() = local.update { it.copy(sheet = null) }

    fun onSheetTitleChange(value: String) = updateSheet { it.copy(title = value.take(MAX_TITLE)) }

    /** 같은 사람을 다시 누르면 담당자 없음 */
    fun onSheetAssigneeChange(userId: Long) = updateSheet { it.copy(assigneeId = if (it.assigneeId == userId) null else userId) }

    fun onSheetDueDateChange(date: LocalDate?) = updateSheet { it.copy(dueDate = date) }

    fun saveSheet() {
        val state = local.value
        val sheet = state.sheet ?: return
        val groupId = state.groupId ?: return
        if (sheet.titleMissing) {
            updateSheet { it.copy(showErrors = true) }
            return
        }
        updateSheet { it.copy(saving = true) }
        viewModelScope.launch {
            val title = sheet.title.trim()
            val result = if (sheet.todo == null) {
                todoRepository.create(groupId, title, sheet.assigneeId, sheet.dueDate)
            } else {
                todoRepository.edit(sheet.todo, TodoEdit(title, sheet.assigneeId, sheet.dueDate))
            }
            if (result is ApiResult.Success) {
                local.update { it.copy(sheet = null) }
                load()
            } else {
                updateSheet { it.copy(saving = false) }
                onFailure(result)
            }
        }
    }

    fun deleteSheetTodo() {
        val todo = local.value.sheet?.todo ?: return
        updateSheet { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = todoRepository.delete(todo)) {
                is ApiResult.Success -> {
                    local.update { state -> state.copy(sheet = null, todos = state.todos.filterNot { it.id == todo.id }) }
                }
                else -> {
                    updateSheet { it.copy(saving = false) }
                    onFailure(result)
                }
            }
        }
    }

    /** 다른 가족이 먼저 고쳤으면(409) 최신 목록을 받아 다시 하게 한다 */
    private suspend fun onFailure(result: ApiResult<*>) {
        if (result.errorCode == ErrorCodes.VERSION_CONFLICT) {
            _events.send(TodoEvent.Message(UiText.Res(R.string.todo_version_conflict)))
            local.update { it.copy(sheet = null) }
            load()
        } else {
            _events.send(TodoEvent.Message(result.toUiText()))
        }
    }

    private fun load() {
        val groupId = local.value.groupId ?: return
        loadJob?.cancel()
        local.update { it.copy(loading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            val result = todoRepository.page(groupId)
            local.update {
                if (result is ApiResult.Success) {
                    it.copy(todos = result.value.items, nextCursor = result.value.nextCursor, loading = false, today = LocalDate.now(clock))
                } else {
                    it.copy(loading = false, loadFailed = true)
                }
            }
        }
    }

    private fun replace(todo: Todo) = local.update { state ->
        state.copy(todos = state.todos.map { if (it.id == todo.id) todo else it })
    }

    private fun updateSheet(transform: (TodoSheet) -> TodoSheet) = local.update { state ->
        state.copy(sheet = state.sheet?.let(transform))
    }

    private companion object {
        /** 서버 할 일 제목 최대 길이 */
        const val MAX_TITLE = 100
    }
}
