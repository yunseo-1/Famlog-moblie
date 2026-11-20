package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.CandidateRepository
import com.famlog.core.data.ChatRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Candidate
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CandidateInboxUiState(
    /** 대기 후보 (신뢰도 구간 무관, 만료 제외), 최신 순 */
    val items: List<Candidate> = emptyList(),
    /** 근거 메시지 ID → 폰에 받아 둔 메시지 (없으면 인용 없이 "원문 보기"만) */
    val anchors: Map<Long, ChatMessage> = emptyMap(),
    val members: List<Member> = emptyList(),
    val busy: Set<Long> = emptySet(),
    val sheet: CandidateSheet? = null,
    val today: LocalDate = LocalDate.MIN,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
)

/** SCR-14 제안함: 대기 중인 후보 전체 (채팅방 카드로 안 뜬 낮은 신뢰도 포함) */
@HiltViewModel
class CandidateInboxViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val candidateRepository: CandidateRepository,
    chatRepository: ChatRepository,
    groupRepository: GroupRepository,
    placeRepository: PlaceRepository,
    clock: Clock,
) : ViewModel() {

    val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    val actions = CandidateActions(viewModelScope, groupId, candidateRepository, groupRepository, placeRepository, clock)
    val events: Flow<CandidateEvent> = actions.events

    private data class LoadState(val loading: Boolean = true, val failed: Boolean = false, val loadingMore: Boolean = false)

    private val load = MutableStateFlow(LoadState())

    private val items = combine(candidateRepository.candidates(groupId), candidateTicker(clock)) { list, now ->
        list.filter { it.waitingAt(now) }.sortedByDescending { it.createdAt } to now.atZone(SeoulZone).toLocalDate()
    }

    private val anchors: Flow<Map<Long, ChatMessage>> = chatRepository.messages(groupId)
        .map { messages -> messages.filter { it.id != null && !it.deleted }.associateBy { it.id!! } }
        .distinctUntilChanged()

    private val members: Flow<List<Member>> = groupRepository.details.map { it[groupId]?.members.orEmpty() }.distinctUntilChanged()

    private val actionState = combine(actions.sheetState, actions.busyState) { sheet, busy -> sheet to busy }

    val uiState: StateFlow<CandidateInboxUiState> = combine(
        items,
        anchors,
        members,
        actionState,
        combine(load, candidateRepository.inboxHasMore(groupId)) { load, more -> load to more },
    ) { (list, today), anchors, members, (sheet, busy), (load, more) ->
        CandidateInboxUiState(
            items = list,
            anchors = anchors,
            members = members,
            busy = busy,
            sheet = sheet,
            today = today,
            loading = load.loading && list.isEmpty(),
            loadFailed = load.failed,
            loadingMore = load.loadingMore,
            hasMore = more,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CandidateInboxUiState())

    init {
        refresh()
    }

    fun refresh() {
        load.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val result = candidateRepository.loadInbox(groupId)
            load.update { it.copy(loading = false, failed = result !is ApiResult.Success) }
        }
    }

    /** 목록 끝에 닿으면 다음 쪽 */
    fun loadMore() {
        val state = load.value
        if (state.loading || state.loadingMore || !uiState.value.hasMore) return
        load.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            candidateRepository.loadInbox(groupId, more = true)
            load.update { it.copy(loadingMore = false) }
        }
    }
}
