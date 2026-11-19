package com.famlog.feature.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceEdit
import com.famlog.core.model.PlaceMarkKind
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

/** 카테고리·별점·한줄평 고치기 시트 */
data class PlaceEditSheet(
    val category: PlaceCategory,
    val rating: Int?,
    val review: String,
    val saving: Boolean = false,
)

data class PlaceDetailUiState(
    val loading: Boolean = true,
    val place: Place? = null,
    /** 지워졌거나 볼 수 없는 장소 (채팅 카드에서 들어왔을 때 404) */
    val missing: Boolean = false,
    val loadFailed: Boolean = false,
    val members: List<Member> = emptyList(),
    val myUserId: Long? = null,
    val myRole: GroupRole = GroupRole.MEMBER,
    val deleting: Boolean = false,
    val editSheet: PlaceEditSheet? = null,
) {
    /** 수정·삭제는 공유한 사람 또는 관리자 이상 (서버와 같은 기준, 아니면 403) */
    val canEdit: Boolean
        get() = place != null && (place.sharedBy == myUserId || myRole != GroupRole.MEMBER)

    fun marked(kind: PlaceMarkKind): Boolean = myUserId != null && place?.marks?.of(kind)?.contains(myUserId) == true

    /** 표시한 구성원 이름 (그룹 순서) */
    fun markedNames(kind: PlaceMarkKind): List<String> {
        val ids = place?.marks?.of(kind).orEmpty()
        return members.filter { it.userId in ids }.map { it.displayName }
    }

    fun nameOf(userId: Long?): String? = userId?.let { id -> members.firstOrNull { it.userId == id }?.displayName }
}

sealed interface PlaceDetailEvent {
    data class Message(val text: UiText) : PlaceDetailEvent
    data object Closed : PlaceDetailEvent
}

/** SCR-32 장소 상세 (PLC-01·05). 다녀옴·가고 싶어요는 구성원 누구나, 수정·삭제는 공유한 사람·관리자 이상 */
@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    val placeId: Long = checkNotNull(savedStateHandle["placeId"])

    private val local = MutableStateFlow(PlaceDetailUiState())

    val uiState: StateFlow<PlaceDetailUiState> = combine(local, groupRepository.details) { state, details ->
        val detail = state.place?.groupId?.let { details[it] }
        state.copy(members = detail?.members.orEmpty(), myRole = detail?.myRole ?: GroupRole.MEMBER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val _events = Channel<PlaceDetailEvent>(Channel.BUFFERED)
    val events: Flow<PlaceDetailEvent> = _events.receiveAsFlow()

    /** 표시 요청이 겹치지 않게 (빠르게 두 번 누르면 마지막 결과만 맞도록 하나씩) */
    private val markBusy = mutableSetOf<PlaceMarkKind>()

    init {
        viewModelScope.launch { local.update { it.copy(myUserId = sessionStore.userId()) } }
        viewModelScope.launch { load() }
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    fun toggleMark(kind: PlaceMarkKind) {
        val state = local.value
        val place = state.place ?: return
        val me = state.myUserId ?: return
        if (!markBusy.add(kind)) return
        val on = !state.marked(kind)
        val before = place.marks
        val optimistic = when (kind) {
            PlaceMarkKind.VISITED -> before.copy(visited = if (on) before.visited + me else before.visited - me)
            PlaceMarkKind.WANT -> before.copy(want = if (on) before.want + me else before.want - me)
        }
        local.update { it.copy(place = it.place?.copy(marks = optimistic)) }
        viewModelScope.launch {
            val result = placeRepository.setMark(place, kind, on)
            if (result is ApiResult.Success) {
                local.update { it.copy(place = it.place?.copy(marks = result.value)) }
            } else {
                local.update { it.copy(place = it.place?.copy(marks = before)) }
                _events.send(PlaceDetailEvent.Message(result.toUiText()))
            }
            markBusy.remove(kind)
        }
    }

    fun openEdit() {
        val place = local.value.place ?: return
        local.update { it.copy(editSheet = PlaceEditSheet(place.category, place.rating, place.oneLineReview.orEmpty())) }
    }

    fun closeEdit() = local.update { it.copy(editSheet = null) }

    fun onEditCategory(category: PlaceCategory) = updateSheet { it.copy(category = category) }

    /** 같은 별을 다시 누르면 별점 없음 */
    fun onEditRating(rating: Int) = updateSheet { it.copy(rating = if (it.rating == rating) null else rating.coerceIn(1, 5)) }

    fun onEditReview(value: String) = updateSheet { it.copy(review = value.take(MAX_REVIEW)) }

    fun saveEdit() {
        val place = local.value.place ?: return
        val sheet = local.value.editSheet ?: return
        if (sheet.saving) return
        updateSheet { it.copy(saving = true) }
        viewModelScope.launch {
            val edit = PlaceEdit(sheet.category, sheet.rating, sheet.review.trim().takeIf { it.isNotEmpty() })
            when (val result = placeRepository.edit(place, edit)) {
                is ApiResult.Success -> local.update { it.copy(place = result.value, editSheet = null) }
                else -> {
                    updateSheet { it.copy(saving = false) }
                    _events.send(PlaceDetailEvent.Message(result.toUiText()))
                }
            }
        }
    }

    fun delete() {
        val place = local.value.place ?: return
        if (local.value.deleting) return
        local.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val result = placeRepository.delete(place)) {
                is ApiResult.Success -> _events.send(PlaceDetailEvent.Closed)
                else -> {
                    local.update { it.copy(deleting = false) }
                    _events.send(PlaceDetailEvent.Message(result.toUiText()))
                }
            }
        }
    }

    private suspend fun load() {
        local.update { it.copy(loadFailed = false) }
        when (val result = placeRepository.detail(placeId)) {
            is ApiResult.Success -> {
                local.update { it.copy(loading = false, place = result.value, missing = false) }
                if (groupRepository.details.value[result.value.groupId] == null) groupRepository.detail(result.value.groupId)
            }
            // 지워졌거나 다른 그룹 장소면 404 → 채팅 카드는 남아 있으므로 "삭제된 장소"로 보인다
            is ApiResult.Failure -> local.update {
                if (result.status == 404) it.copy(loading = false, missing = true) else it.copy(loading = false, loadFailed = true)
            }
            ApiResult.NetworkError -> local.update { it.copy(loading = false, loadFailed = true) }
        }
    }

    private fun updateSheet(transform: (PlaceEditSheet) -> PlaceEditSheet) = local.update { state ->
        state.copy(editSheet = state.editSheet?.let(transform))
    }

    private companion object {
        /** 서버 한줄평 최대 길이 */
        const val MAX_REVIEW = 100
    }
}
