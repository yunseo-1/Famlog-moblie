package com.famlog.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.GroupRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ChatListUiState(
    val groups: List<GroupSummary> = emptyList(),
    /** 미리보기 "동생: …"의 보낸 사람 이름 */
    val details: Map<Long, GroupDetail> = emptyMap(),
    val myUserId: Long? = null,
    val today: LocalDate = LocalDate.MIN,
) {
    fun senderName(group: GroupSummary): String? {
        val senderId = group.lastMessage?.senderId ?: return null
        if (senderId == myUserId) return null
        return details[group.id]?.member(senderId)?.displayName
    }
}

/** SCR-10 채팅 탭: 내 그룹 전체 (현재 그룹과 관계없이), 최근 메시지가 위로 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
) : ViewModel() {

    private val myUserId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ChatListUiState> = combine(
        groupRepository.groups,
        groupRepository.details,
        myUserId,
    ) { groups, details, me ->
        ChatListUiState(
            groups = groups.sortedByDescending { it.lastMessage?.sentAt },
            details = details,
            myUserId = me,
            today = LocalDate.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListUiState())

    init {
        viewModelScope.launch { myUserId.value = sessionStore.userId() }
    }

    /** 화면에 돌아올 때마다: 안 읽은 수를 서버 값으로 맞추고, 이름을 모르는 그룹의 구성원을 받는다 */
    fun refresh() {
        viewModelScope.launch {
            groupRepository.refreshGroups()
            val known = groupRepository.details.value.keys
            groupRepository.groups.value.filter { it.id !in known }.forEach { groupRepository.detail(it.id) }
        }
    }
}
