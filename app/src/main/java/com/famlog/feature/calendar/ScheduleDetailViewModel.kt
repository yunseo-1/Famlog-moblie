package com.famlog.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.ScheduleRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Schedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleDetailUiState(
    val loading: Boolean = true,
    val schedule: Schedule? = null,
    val members: List<Member> = emptyList(),
    val myUserId: Long? = null,
    val myRole: GroupRole = GroupRole.MEMBER,
    val deleting: Boolean = false,
) {
    /** 수정·삭제는 등록한 사람 또는 관리자 이상 (서버와 같은 기준, 아니면 403) */
    val canEdit: Boolean
        get() = schedule != null && (schedule.createdBy == myUserId || myRole != GroupRole.MEMBER)

    fun nameOf(userId: Long?): String? = userId?.let { id -> members.firstOrNull { it.userId == id }?.displayName }
}

sealed interface ScheduleDetailEvent {
    data class Message(val text: UiText) : ScheduleDetailEvent
    data object Closed : ScheduleDetailEvent
}

/** SCR-21 일정 상세. 반복 일정은 시리즈 정보를 보인다 */
@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduleRepository: ScheduleRepository,
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    val scheduleId: Long = checkNotNull(savedStateHandle["scheduleId"])

    private val local = MutableStateFlow(ScheduleDetailUiState())

    val uiState: StateFlow<ScheduleDetailUiState> = combine(local, groupRepository.details) { state, details ->
        val detail = state.schedule?.groupId?.let { details[it] }
        state.copy(members = detail?.members.orEmpty(), myRole = detail?.myRole ?: GroupRole.MEMBER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** 지웠거나 없어서 닫는 중이면 더 받지 않는다 (삭제 직후 changes로 다시 받으면 404 안내가 뜬다) */
    private var closing = false

    private val _events = Channel<ScheduleDetailEvent>(Channel.BUFFERED)
    val events: Flow<ScheduleDetailEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { local.update { it.copy(myUserId = sessionStore.userId()) } }
        viewModelScope.launch { load() }
        // 수정 화면에서 저장하고 돌아오면 다시 받는다
        viewModelScope.launch {
            scheduleRepository.changes.collect { groupId -> if (groupId == local.value.schedule?.groupId) load() }
        }
    }

    fun delete() {
        val schedule = local.value.schedule ?: return
        if (local.value.deleting) return
        local.update { it.copy(deleting = true) }
        closing = true
        viewModelScope.launch {
            when (val result = scheduleRepository.delete(schedule)) {
                is ApiResult.Success -> _events.send(ScheduleDetailEvent.Closed)
                else -> {
                    closing = false
                    _events.send(ScheduleDetailEvent.Message(result.toUiText()))
                }
            }
            local.update { it.copy(deleting = false) }
        }
    }

    private suspend fun load() {
        if (closing) return
        when (val result = scheduleRepository.detail(scheduleId)) {
            is ApiResult.Success -> {
                local.update { it.copy(loading = false, schedule = result.value) }
                if (groupRepository.details.value[result.value.groupId] == null) groupRepository.detail(result.value.groupId)
            }
            // 삭제됐거나 나간 그룹이면 404 → 안내하고 닫는다
            else -> {
                closing = true
                _events.send(ScheduleDetailEvent.Message(result.toUiText()))
                _events.send(ScheduleDetailEvent.Closed)
            }
        }
    }
}
