package com.famlog.feature.chat

import com.famlog.R
import com.famlog.core.data.CandidateRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateEvidence
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.DismissReason
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.Member
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceOption
import com.famlog.core.model.errorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

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

sealed interface CandidateEvent {
    data class Message(val text: UiText) : CandidateEvent

    /** 근거 메시지로 스크롤하고 [spans]를 강조한다 (메시지 ID → 추출 부분) */
    data class ShowEvidence(val seq: Long, val spans: Map<Long, List<String>>) : CandidateEvent

    /** Resolved Chip [보기] */
    data class OpenEntity(val type: CandidateEntity, val id: Long) : CandidateEvent
}

/**
 * 후보 처리(등록·무시·되돌리기·원문 보기)와 수정 시트(SCR-13). 채팅방 카드와 제안함(SCR-14)이 함께 쓴다.
 * ViewModel이 자기 [scope]로 만들어 들고 있는다.
 */
class CandidateActions(
    private val scope: CoroutineScope,
    private val groupId: Long,
    private val candidateRepository: CandidateRepository,
    private val groupRepository: GroupRepository,
    private val placeRepository: PlaceRepository,
    private val clock: Clock,
) {
    private val sheet = MutableStateFlow<CandidateSheet?>(null)
    private val busy = MutableStateFlow<Set<Long>>(emptySet())

    /** 열린 수정 시트 */
    val sheetState: StateFlow<CandidateSheet?> = sheet.asStateFlow()

    /** 처리 중인 후보 (버튼을 막는다) */
    val busyState: StateFlow<Set<Long>> = busy.asStateFlow()

    private val _events = Channel<CandidateEvent>(Channel.BUFFERED)
    val events: Flow<CandidateEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null

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
        scope.launch { _events.send(CandidateEvent.OpenEntity(type, id)) }
    }

    /** 근거 인용·원문 보기: 근거 메시지들과 추출 부분을 받아 첫 근거로 스크롤한다 */
    fun showEvidence(candidate: Candidate) = showEvidenceOf(candidate.id)

    /**
     * 제안함·일정·할 일·장소의 원문 보기는 후보 ID만 안다.
     * [fallbackSeq](원문 메시지 위치)를 알면 그 메시지로 가고, 후보를 못 받으면 그 메시지 전체를 강조한다.
     */
    fun showEvidenceOf(candidateId: Long, fallbackSeq: Long? = null) {
        scope.launch {
            val result = candidateRepository.detail(candidateId)
            val evidence = (result as? ApiResult.Success)?.value?.evidence.orEmpty()
            val target = evidence.firstOrNull { it.seq == fallbackSeq } ?: evidence.minByOrNull { it.seq }
            when {
                target != null -> _events.send(CandidateEvent.ShowEvidence(target.seq, evidence.associate { it.messageId to it.spans }))
                fallbackSeq != null -> _events.send(CandidateEvent.ShowEvidence(fallbackSeq, emptyMap()))
                else -> _events.send(CandidateEvent.Message(result.toUiText()))
            }
        }
    }

    fun showEvidence(evidence: CandidateEvidence) {
        scope.launch { _events.send(CandidateEvent.ShowEvidence(evidence.seq, mapOf(evidence.messageId to evidence.spans))) }
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
        searchJob = scope.launch {
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
        // 시트를 열 때 구성원을 받아 두었다
        val approval = form.toApproval(groupRepository.details.value[groupId]?.members.orEmpty(), SeoulZone)
        if (approval == null) return
        sheet.update { it?.copy(saving = true) }
        scope.launch {
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
        scope.launch {
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
        scope.launch {
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

    private companion object {
        const val MAX_LOCATION = 100
        const val MAX_QUERY = 50
    }
}

/** 카드·칩 버튼 → 이 처리기 */
fun CandidateActions.cardActions() = CandidateCardActions(
    onApprove = ::approve,
    onEdit = ::edit,
    onDismiss = ::dismiss,
    onUndo = ::undo,
    onOpen = ::openEntity,
    onShowEvidence = ::showEvidence,
)

/** 수정 시트 입력 → 이 처리기. 근거를 누르면 시트를 닫고 원문으로 간다 */
fun CandidateActions.sheetActions() = CandidateSheetActions(
    onDismiss = ::closeSheet,
    onSubmit = ::submitSheet,
    onShowEvidence = { evidence ->
        closeSheet()
        showEvidence(evidence)
    },
    onTitleChange = ::onTitleChange,
    onDateChange = ::onDateChange,
    onClearDueDate = ::clearDueDate,
    onAllDayChange = ::onAllDayChange,
    onStartTimeChange = ::onStartTimeChange,
    onEndTimeChange = ::onEndTimeChange,
    onToggleParticipant = ::toggleParticipant,
    onLocationChange = ::onLocationChange,
    onAssigneeChange = ::onAssigneeChange,
    onPlaceSelect = ::onPlaceSelect,
    onCategoryChange = ::onCategoryChange,
    onReviewChange = ::onReviewChange,
    onQueryChange = ::onQueryChange,
    onSearch = ::searchPlace,
)
