package com.famlog.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.StartDestination
import com.famlog.core.data.StartResolver
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.network.SessionEvent
import com.famlog.core.network.SessionEvents
import com.famlog.feature.group.GroupSwitcherState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainEvent {
    data class Navigate(val destination: StartDestination) : MainEvent
    data class ShareInvite(val groupName: String, val code: String) : MainEvent
    data class Message(val text: UiText) : MainEvent

    /** 전환을 마쳤거나 다른 화면으로 가므로 시트를 닫는다 */
    data object CloseSwitcher : MainEvent
}

/** 앱 전체 상태: 세션 만료, 앱 바의 현재 그룹, 그룹 전환 시트 (GRP-03, SCR-70) */
@HiltViewModel
class MainViewModel @Inject constructor(
    sessionEvents: SessionEvents,
    private val groupRepository: GroupRepository,
    private val startResolver: StartResolver,
) : ViewModel() {
    val sessionEvents: Flow<SessionEvent> = sessionEvents.events

    private val busy = MutableStateFlow(GroupSwitcherState())

    val switcherState: StateFlow<GroupSwitcherState> = combine(
        groupRepository.groups,
        groupRepository.currentGroupId,
        busy,
    ) { groups, currentId, busy ->
        busy.copy(groups = groups, currentGroupId = currentId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupSwitcherState())

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    /** 시트를 열 때와 프로세스 재시작으로 탭 화면부터 복원됐을 때 목록을 새로 받는다 */
    fun refreshGroups() {
        viewModelScope.launch { groupRepository.refreshGroups() }
    }

    fun switchTo(groupId: Long) {
        if (busy.value.switchingGroupId != null) return
        viewModelScope.launch {
            if (groupRepository.currentGroupId.first() == groupId) {
                _events.send(MainEvent.CloseSwitcher)
                return@launch
            }
            busy.update { it.copy(switchingGroupId = groupId) }
            val result = groupRepository.switchTo(groupId)
            if (result is ApiResult.Success) {
                // 이 그룹에서 아직 가족 역할을 정하지 않았으면 SCR-03으로 (GRP-04)
                val destination = startResolver.enter(groupId)
                _events.send(MainEvent.CloseSwitcher)
                if (destination is StartDestination.RoleSetup) _events.send(MainEvent.Navigate(destination))
            } else {
                _events.send(MainEvent.Message(result.toUiText()))
            }
            busy.update { it.copy(switchingGroupId = null) }
        }
    }

    /** 현재 그룹 초대 코드를 발급해 공유한다 (그룹장·관리자, GRP-02). 링크는 도메인이 정해지면 붙인다 */
    fun invite() {
        if (busy.value.inviting) return
        busy.update { it.copy(inviting = true) }
        viewModelScope.launch {
            val currentId = groupRepository.currentGroupId.first()
            val group = groupRepository.groups.value.firstOrNull { it.id == currentId }
            if (group == null) {
                busy.update { it.copy(inviting = false) }
                return@launch
            }
            when (val result = groupRepository.issueInvite(group.id)) {
                is ApiResult.Success -> _events.send(MainEvent.ShareInvite(group.name, result.value.code))
                else -> _events.send(MainEvent.Message(result.toUiText()))
            }
            busy.update { it.copy(inviting = false) }
        }
    }
}
