package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.CandidateRepository
import com.famlog.core.data.ChatRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.CandidateSurface
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class CandidateUiState(
    /** 근거 메시지 ID → 그 아래 붙일 카드·칩 (만든 순) */
    val byAnchor: Map<Long, List<Candidate>> = emptyMap(),
    /** [되돌리기]를 보일 후보: 승인한 사람·관리자 이상, 승인 후 5분 안 */
    val undoable: Set<Long> = emptySet(),
    /** 처리 중인 후보 (버튼을 막는다) */
    val busy: Set<Long> = emptySet(),
    val members: List<Member> = emptyList(),
    val sheet: CandidateSheet? = null,
    val today: LocalDate = LocalDate.MIN,
    /** 제안함 배지: 받아 둔 대기 후보 수. [pendingMore]면 "N+" */
    val pending: Int = 0,
    val pendingMore: Boolean = false,
    /** 변경 후보의 [바꾸기]를 보일지 (내가 등록한 일정·관리자 이상) */
    val viewer: CandidateViewer = CandidateViewer(),
    /** 대상이 여럿인 변경·완료 후보에서 고른 대상 */
    val picks: Map<Long, Long> = emptyMap(),
) {
    fun nameOf(userId: Long?): String? = userId?.let { id -> members.firstOrNull { it.userId == id }?.displayName }
}

/** 채팅방 후보 카드·Resolved Chip·수정 시트 (ARC-05, SCR-13) + 제안함 배지 */
@HiltViewModel
class CandidateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val candidateRepository: CandidateRepository,
    private val chatRepository: ChatRepository,
    groupRepository: GroupRepository,
    placeRepository: PlaceRepository,
    private val sessionStore: SessionStore,
    clock: Clock,
) : ViewModel() {

    val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    /** 등록·무시·되돌리기·원문 보기·수정 시트 */
    val actions = CandidateActions(viewModelScope, groupId, candidateRepository, groupRepository, placeRepository, clock)
    val events: Flow<CandidateEvent> = actions.events

    private val myUserId = MutableStateFlow<Long?>(null)
    private val ticker: Flow<Instant> = candidateTicker(clock)

    private val members: Flow<List<Member>> = groupRepository.details.map { it[groupId]?.members.orEmpty() }.distinctUntilChanged()
    private val myRole: Flow<GroupRole> = groupRepository.myRole(groupId)

    private data class Cards(
        val byAnchor: Map<Long, List<Candidate>>,
        val undoable: Set<Long>,
        val pending: Int,
        val now: Instant,
        val viewer: CandidateViewer,
    )

    private val cards = combine(candidateRepository.candidates(groupId), ticker, myUserId, myRole) { list, now, me, role ->
        val shown = list.filter { it.visibleInChat(now) }
        Cards(
            byAnchor = shown.filter { it.anchorMessageId != null }.sortedBy { it.createdAt }.groupBy { it.anchorMessageId!! },
            undoable = shown.filter { it.undoableBy(me, role, now) }.map { it.id }.toSet(),
            pending = list.count { it.waitingAt(now) },
            now = now,
            viewer = CandidateViewer(me, role),
        )
    }

    val uiState: StateFlow<CandidateUiState> = combine(
        cards,
        members,
        actions.actionState(),
        candidateRepository.inboxHasMore(groupId),
    ) { cards, members, action, more ->
        CandidateUiState(
            byAnchor = cards.byAnchor,
            undoable = cards.undoable,
            busy = action.busy,
            members = members,
            sheet = action.sheet,
            today = cards.now.atZone(SeoulZone).toLocalDate(),
            pending = cards.pending,
            pendingMore = more,
            viewer = cards.viewer,
            picks = action.picks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CandidateUiState())

    init {
        viewModelScope.launch { myUserId.value = sessionStore.userId() }
        // 제안함 배지: 대기 후보 첫 쪽
        viewModelScope.launch { candidateRepository.loadInbox(groupId) }
        // 받아 둔 가장 오래된 메시지부터 끝까지. 이전 메시지를 더 받으면 그 범위만 더 받는다
        viewModelScope.launch {
            var loadedFrom: Long? = null
            chatRepository.messages(groupId)
                .map { list -> list.firstNotNullOfOrNull { it.seq } }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { minSeq ->
                    val from = loadedFrom
                    when {
                        from == null -> candidateRepository.loadForChat(groupId, minSeq, null)
                        minSeq < from -> candidateRepository.loadForChat(groupId, minSeq, from - 1)
                        else -> return@collect
                    }
                    loadedFrom = minSeq
                }
        }
    }

    /** 화면에 다시 들어왔다(제안함에서 돌아온 경우 포함) → 끊긴 동안 놓친 처리 결과를 받는다 */
    fun refresh() {
        viewModelScope.launch {
            candidateRepository.loadInbox(groupId)
            val minSeq = chatRepository.messages(groupId).first().firstNotNullOfOrNull { it.seq } ?: return@launch
            candidateRepository.loadForChat(groupId, minSeq, null)
        }
    }

    private fun Candidate.visibleInChat(now: Instant): Boolean = when (status) {
        CandidateStatus.PENDING -> surface == CandidateSurface.CARD && waitingAt(now)
        CandidateStatus.APPROVED -> true
        else -> false
    }

    private fun Candidate.undoableBy(me: Long?, role: GroupRole, now: Instant): Boolean {
        if (status != CandidateStatus.APPROVED) return false
        val at = decidedAt ?: return false
        val mine = me != null && decidedBy == me
        return (mine || role != GroupRole.MEMBER) && now.isBefore(at.plus(UNDO_WINDOW))
    }

    companion object {
        val UNDO_WINDOW: Duration = Duration.ofMinutes(5)
        const val TICK_MS = 15_000L
    }
}

/** 카드를 보는 사람. 변경 후보의 권한 판단에 쓴다 */
data class CandidateViewer(val userId: Long? = null, val role: GroupRole = GroupRole.MEMBER)

/** 시트·처리 중·고른 대상 */
internal data class CandidateActionState(val sheet: CandidateSheet?, val busy: Set<Long>, val picks: Map<Long, Long>)

internal fun CandidateActions.actionState(): Flow<CandidateActionState> =
    combine(sheetState, busyState, pickState) { sheet, busy, picks -> CandidateActionState(sheet, busy, picks) }

/** 그룹에서 내 역할 (목록·상세 중 아는 쪽) */
internal fun GroupRepository.myRole(groupId: Long): Flow<GroupRole> = combine(groups, details) { groups, details ->
    groups.firstOrNull { it.id == groupId }?.myRole ?: details[groupId]?.myRole ?: GroupRole.MEMBER
}.distinctUntilChanged()

/** 대기 중이고 아직 만료 전 */
internal fun Candidate.waitingAt(now: Instant): Boolean =
    status == CandidateStatus.PENDING && (expiresAt == null || expiresAt.isAfter(now))

/** 되돌리기 5분·만료를 다시 따지도록 시각을 흘린다 */
internal fun candidateTicker(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(CandidateViewModel.TICK_MS)
    }
}
