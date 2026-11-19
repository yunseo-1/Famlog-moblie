package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.CandidateRepository
import com.famlog.core.data.ChatRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateApproval
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateEvidence
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.CandidateSurface
import com.famlog.core.model.DismissReason
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceOption
import com.famlog.core.model.errorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** 수정 시트(SCR-13). [evidence]·[form]은 상세를 받은 뒤 채워진다 */
data class CandidateSheet(
    val candidate: Candidate,
    val evidence: List<CandidateEvidence> = emptyList(),
    val form: CandidateForm? = null,
    /** 서버가 422로 알려 준 칸 (골라야 할 사람·장소 포함). "확인 필요"로 강조한다 */
    val serverMissing: Set<String> = emptySet(),
    val saving: Boolean = false,
) {
    /** "입력해 주세요"를 보일 빈 칸. 하나라도 있으면 [등록]을 막는다 (Figma 02-3) */
    val errors: Set<String> get() = form?.missing.orEmpty()

    val canSubmit: Boolean get() = form != null && form.missing.isEmpty() && !saving
}

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
) {
    fun nameOf(userId: Long?): String? = userId?.let { id -> members.firstOrNull { it.userId == id }?.displayName }
}

sealed interface CandidateEvent {
    data class Message(val text: UiText) : CandidateEvent

    /** 근거 메시지로 스크롤하고 [spans]를 강조한다 (메시지 ID → 추출 부분) */
    data class ShowEvidence(val seq: Long, val spans: Map<Long, List<String>>) : CandidateEvent

    /** Resolved Chip [보기] */
    data class OpenEntity(val type: CandidateEntity, val id: Long) : CandidateEvent
}

/** 채팅방 후보 카드·Resolved Chip·수정 시트 (ARC-05, SCR-13) */
@HiltViewModel
class CandidateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val candidateRepository: CandidateRepository,
    private val chatRepository: ChatRepository,
    private val groupRepository: GroupRepository,
    private val placeRepository: PlaceRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
) : ViewModel() {

    val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val myUserId = MutableStateFlow<Long?>(null)
    private val sheet = MutableStateFlow<CandidateSheet?>(null)
    private val busy = MutableStateFlow<Set<Long>>(emptySet())

    /** 되돌리기 5분·만료를 다시 따지도록 시각을 흘린다 */
    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MS)
        }
    }

    private val members: Flow<List<Member>> = groupRepository.details.map { it[groupId]?.members.orEmpty() }.distinctUntilChanged()
    private val myRole: Flow<GroupRole> = combine(groupRepository.groups, groupRepository.details) { groups, details ->
        groups.firstOrNull { it.id == groupId }?.myRole ?: details[groupId]?.myRole ?: GroupRole.MEMBER
    }.distinctUntilChanged()

    private val cards = combine(candidateRepository.candidates(groupId), ticker, myUserId, myRole) { list, now, me, role ->
        val shown = list.filter { it.visibleInChat(now) }
        Triple(
            shown.filter { it.anchorMessageId != null }.sortedBy { it.createdAt }.groupBy { it.anchorMessageId!! },
            shown.filter { it.undoableBy(me, role, now) }.map { it.id }.toSet(),
            now,
        )
    }

    val uiState: StateFlow<CandidateUiState> = combine(cards, members, sheet, busy) { (byAnchor, undoable, now), members, sheet, busy ->
        CandidateUiState(
            byAnchor = byAnchor,
            undoable = undoable,
            busy = busy,
            members = members,
            sheet = sheet,
            today = now.atZone(SeoulZone).toLocalDate(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CandidateUiState())

    private val _events = Channel<CandidateEvent>(Channel.BUFFERED)
    val events: Flow<CandidateEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { myUserId.value = sessionStore.userId() }
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

    /** 화면에 다시 들어왔다 → 끊긴 동안 놓친 처리 결과를 받는다 */
    fun refresh() {
        viewModelScope.launch {
            val minSeq = chatRepository.messages(groupId).first().firstNotNullOfOrNull { it.seq } ?: return@launch
            candidateRepository.loadForChat(groupId, minSeq, null)
        }
    }

    /** [등록]: 후보 값 그대로. 채울 칸이 있으면 서버가 422 → 시트를 연다 */
    fun approve(candidate: Candidate) = act(candidate) {
        val result = candidateRepository.approve(candidate, approval = null)
        if (result.errorCode == ErrorCodes.CANDIDATE_MISSING_FIELDS) {
            val fields = (result as ApiResult.Failure).error?.fields.orEmpty().toSet()
            openSheet(candidate, fields)
        } else {
            handle(candidate, result)
        }
    }

    fun dismiss(candidate: Candidate, reason: DismissReason?) = act(candidate) {
        handle(candidate, candidateRepository.dismiss(candidate, reason))
    }

    fun undo(candidate: Candidate) = act(candidate) {
        val result = candidateRepository.undo(candidate)
        if (result is ApiResult.Success) {
            _events.send(CandidateEvent.Message(UiText.Res(R.string.candidate_undone)))
        } else {
            handle(candidate, result)
        }
    }

    fun openEntity(candidate: Candidate) {
        val type = candidate.entityType ?: return
        val id = candidate.entityId ?: return
        viewModelScope.launch { _events.send(CandidateEvent.OpenEntity(type, id)) }
    }

    /** 근거 인용·원문 보기: 근거 메시지들과 추출 부분을 받아 첫 근거로 스크롤한다 */
    fun showEvidence(candidate: Candidate) {
        viewModelScope.launch {
            when (val result = candidateRepository.detail(candidate.id)) {
                is ApiResult.Success -> {
                    val evidence = result.value.evidence
                    val first = evidence.minByOrNull { it.seq } ?: return@launch
                    _events.send(CandidateEvent.ShowEvidence(first.seq, evidence.associate { it.messageId to it.spans }))
                }
                else -> _events.send(CandidateEvent.Message(result.toUiText()))
            }
        }
    }

    fun showEvidence(evidence: CandidateEvidence) {
        viewModelScope.launch { _events.send(CandidateEvent.ShowEvidence(evidence.seq, mapOf(evidence.messageId to evidence.spans))) }
    }

    // ---- 수정 시트 (SCR-13) ----

    fun edit(candidate: Candidate) = openSheet(candidate, emptySet())

    fun closeSheet() {
        searchJob?.cancel()
        sheet.value = null
    }

    fun onTitleChange(value: String) = updateForm { form ->
        val title = value.take(CandidateForm.MAX_TITLE)
        when (form) {
            is CandidateForm.Schedule -> form.copy(title = title)
            is CandidateForm.Anniversary -> form.copy(title = title)
            is CandidateForm.Todo -> form.copy(title = title)
            is CandidateForm.Place -> form
        }
    }

    fun onDateChange(date: LocalDate) = updateForm { form ->
        when (form) {
            is CandidateForm.Schedule -> form.copy(date = date)
            is CandidateForm.Anniversary -> form.copy(date = date)
            is CandidateForm.Todo -> form.copy(dueDate = date)
            is CandidateForm.Place -> form
        }
    }

    fun clearDueDate() = updateForm { form -> if (form is CandidateForm.Todo) form.copy(dueDate = null) else form }

    fun onAllDayChange(allDay: Boolean) = updateSchedule { it.copy(allDay = allDay) }

    fun onStartTimeChange(time: LocalTime) = updateSchedule { form ->
        // 길이를 지키며 끝도 옮긴다 (일정 편집과 같다)
        val length = Duration.between(form.startTime, form.endTime).takeIf { !it.isNegative } ?: Duration.ZERO
        val end = time.plus(length).takeIf { !it.isBefore(time) } ?: time
        form.copy(startTime = time, endTime = end)
    }

    fun onEndTimeChange(time: LocalTime) = updateSchedule { form -> form.copy(endTime = time.takeIf { !it.isBefore(form.startTime) } ?: form.startTime) }

    fun toggleParticipant(userId: Long) = updateSchedule { form ->
        val ids = form.participantIds
        form.copy(participantIds = if (userId in ids) ids - userId else ids + userId)
    }

    fun onLocationChange(value: String) = updateSchedule { it.copy(locationText = value.take(MAX_LOCATION)) }

    fun onAssigneeChange(userId: Long) = updateForm { form ->
        if (form is CandidateForm.Todo) form.copy(assigneeId = if (form.assigneeId == userId) null else userId) else form
    }

    fun onPlaceSelect(option: PlaceOption) = updatePlace { form ->
        val options = if (form.options.any { it.kakaoPlaceId == option.kakaoPlaceId }) form.options else form.options + option
        form.copy(options = options, selected = option, category = option.category, results = emptyList(), searched = false)
    }

    fun onCategoryChange(category: PlaceCategory) = updatePlace { it.copy(category = category) }

    fun onReviewChange(value: String) = updatePlace { it.copy(review = value.take(CandidateForm.MAX_REVIEW)) }

    fun onQueryChange(value: String) = updatePlace { it.copy(query = value.take(MAX_QUERY)) }

    /** 서버가 찾은 곳이 아니면 다시 검색한다 (카카오 장소 검색, PLC-01과 같은 API) */
    fun searchPlace() {
        val form = sheet.value?.form as? CandidateForm.Place ?: return
        val query = form.query.trim()
        if (query.isEmpty() || form.searching) return
        updatePlace { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            val result = placeRepository.search(query, page = 1, groupId = groupId)
            val results = (result as? ApiResult.Success)?.value?.items.orEmpty().map {
                PlaceOption(it.kakaoPlaceId, it.name, it.suggestedCategory, it.categoryDetail, it.address, it.region1, it.region2)
            }
            updatePlace { it.copy(results = results, searching = false, searched = true) }
            if (result !is ApiResult.Success) _events.send(CandidateEvent.Message(result.toUiText()))
        }
    }

    fun submitSheet() {
        val current = sheet.value ?: return
        val form = current.form ?: return
        if (current.saving) return
        val approval = form.toApproval(uiState.value.members, SeoulZone)
        if (approval == null) return
        sheet.update { it?.copy(saving = true) }
        viewModelScope.launch {
            val result = candidateRepository.approve(current.candidate, approval)
            if (result.errorCode == ErrorCodes.CANDIDATE_MISSING_FIELDS) {
                val fields = (result as ApiResult.Failure).error?.fields.orEmpty().toSet()
                sheet.update { it?.copy(saving = false, serverMissing = fields) }
                _events.send(CandidateEvent.Message(result.toUiText()))
            } else {
                sheet.update { it?.copy(saving = false) }
                handle(current.candidate, result)
            }
        }
    }

    private fun openSheet(candidate: Candidate, serverMissing: Set<String>) {
        sheet.value = CandidateSheet(candidate, serverMissing = serverMissing)
        viewModelScope.launch {
            val detail = candidateRepository.detail(candidate.id)
            val members = membersOf()
            val fresh = (detail as? ApiResult.Success)?.value
            val target = fresh?.candidate ?: candidate
            if (target.status != CandidateStatus.PENDING) {
                sheet.value = null
                return@launch
            }
            val evidence = fresh?.evidence.orEmpty()
            sheet.update { current ->
                current?.takeIf { it.candidate.id == candidate.id }?.copy(
                    candidate = target,
                    evidence = evidence,
                    form = target.toForm(members, evidence, today()),
                )
            }
        }
    }

    /**
     * 처리 결과. 다른 가족이 먼저 처리했으면 서버 문구("엄마님이 먼저 등록했어요.")를 그대로 보이고 최신 상태를 받는다.
     */
    private suspend fun handle(candidate: Candidate, result: ApiResult<Candidate>) {
        when {
            result is ApiResult.Success -> {
                if (sheet.value?.candidate?.id == candidate.id) sheet.value = null
            }
            result.errorCode == ErrorCodes.CANDIDATE_ALREADY_DECIDED ||
                result.errorCode == ErrorCodes.CANDIDATE_EXPIRED -> {
                closeSheetOf(candidate)
                _events.send(CandidateEvent.Message(result.toUiText()))
                candidateRepository.detail(candidate.id)
            }
            result.errorCode == ErrorCodes.VERSION_CONFLICT -> {
                closeSheetOf(candidate)
                _events.send(CandidateEvent.Message(UiText.Res(R.string.candidate_changed)))
                candidateRepository.detail(candidate.id)
            }
            else -> _events.send(CandidateEvent.Message(result.toUiText()))
        }
    }

    private fun closeSheetOf(candidate: Candidate) {
        if (sheet.value?.candidate?.id == candidate.id) sheet.value = null
    }

    /** 같은 후보를 두 번 보내지 않는다 */
    private fun act(candidate: Candidate, block: suspend () -> Unit) {
        if (candidate.id in busy.value) return
        busy.update { it + candidate.id }
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.update { it - candidate.id }
            }
        }
    }

    private suspend fun membersOf(): List<Member> {
        groupRepository.details.value[groupId]?.let { return it.members }
        return (groupRepository.detail(groupId) as? ApiResult.Success)?.value?.members.orEmpty()
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(SeoulZone))

    private fun updateForm(transform: (CandidateForm) -> CandidateForm) = sheet.update { current ->
        val form = current?.form ?: return@update current
        current.copy(form = transform(form))
    }

    private fun updateSchedule(transform: (CandidateForm.Schedule) -> CandidateForm.Schedule) =
        updateForm { if (it is CandidateForm.Schedule) transform(it) else it }

    private fun updatePlace(transform: (CandidateForm.Place) -> CandidateForm.Place) =
        updateForm { if (it is CandidateForm.Place) transform(it) else it }

    private fun Candidate.visibleInChat(now: Instant): Boolean = when (status) {
        CandidateStatus.PENDING -> surface == CandidateSurface.CARD && (expiresAt == null || expiresAt.isAfter(now))
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
        private const val MAX_LOCATION = 100
        private const val MAX_QUERY = 50
    }
}
