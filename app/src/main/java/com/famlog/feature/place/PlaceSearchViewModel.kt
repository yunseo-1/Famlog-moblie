package com.famlog.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceSearchItem
import com.famlog.core.model.PlaceShare
import com.famlog.core.model.errorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 공유 시트 입력. 카테고리는 서버 추천값으로 시작한다 */
data class PlaceShareSheet(
    val item: PlaceSearchItem,
    val category: PlaceCategory = item.suggestedCategory,
    val rating: Int? = null,
    val review: String = "",
    val shareToChat: Boolean = true,
    val saving: Boolean = false,
)

data class PlaceSearchUiState(
    val groupId: Long? = null,
    val query: String = "",
    /** 지금 보이는 결과의 검색어 (입력 중인 [query]와 다를 수 있다) */
    val searchedQuery: String = "",
    val results: List<PlaceSearchItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val searching: Boolean = false,
    val loadingMore: Boolean = false,
    val searchFailed: Boolean = false,
    val sheet: PlaceShareSheet? = null,
) {
    val noResults: Boolean get() = searchedQuery.isNotEmpty() && !searching && !searchFailed && results.isEmpty()
}

sealed interface PlaceSearchEvent {
    data class Message(val text: UiText) : PlaceSearchEvent

    /** 공유했거나 이미 공유된 장소를 골랐다 → 상세로 */
    data class OpenPlace(val placeId: Long) : PlaceSearchEvent
}

/**
 * SCR-32 장소 검색·공유 (PLC-01). 입력을 멈추면 잠시 뒤 검색하고, 키보드의 검색을 누르면 바로 한다.
 * 검색은 서버가 카카오 로컬을 대신 부르며 분당 30회 제한이 있다.
 */
@HiltViewModel
class PlaceSearchViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    groupRepository: GroupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceSearchUiState())
    val uiState: StateFlow<PlaceSearchUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlaceSearchEvent>(Channel.BUFFERED)
    val events: Flow<PlaceSearchEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val groupId = groupRepository.currentGroupId.filterNotNull().first()
            _uiState.update { it.copy(groupId = groupId) }
        }
    }

    fun onQueryChange(value: String) {
        val query = value.take(MAX_QUERY)
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchedQuery = "", results = emptyList(), hasMore = false, searching = false, searchFailed = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(query.trim())
        }
    }

    /** 키보드 [검색] */
    fun searchNow() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(query) }
    }

    fun retry() = searchNow()

    /** 목록 끝에 가까워지면 다음 쪽 (서버 최대 45쪽) */
    fun loadMore() {
        val state = _uiState.value
        val groupId = state.groupId ?: return
        if (!state.hasMore || state.loadingMore || state.searching || state.page >= MAX_PAGE) return
        val query = state.searchedQuery
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = placeRepository.search(query, state.page + 1, groupId)
            _uiState.update { current ->
                if (current.searchedQuery != query) return@update current.copy(loadingMore = false)
                if (result is ApiResult.Success) {
                    current.copy(
                        results = current.results + result.value.items.filter { new ->
                            current.results.none { it.kakaoPlaceId == new.kakaoPlaceId }
                        },
                        page = state.page + 1,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
                } else {
                    current.copy(loadingMore = false)
                }
            }
            if (result !is ApiResult.Success) _events.send(PlaceSearchEvent.Message(result.toUiText()))
        }
    }

    /** 이미 우리 그룹에 공유된 장소면 상세로, 아니면 공유 시트 */
    fun select(item: PlaceSearchItem) {
        val sharedId = item.sharedPlaceId
        if (sharedId != null) {
            _events.trySend(PlaceSearchEvent.OpenPlace(sharedId))
        } else {
            _uiState.update { it.copy(sheet = PlaceShareSheet(item)) }
        }
    }

    fun closeSheet() = _uiState.update { it.copy(sheet = null) }

    fun onCategoryChange(category: PlaceCategory) = updateSheet { it.copy(category = category) }

    /** 같은 별을 다시 누르면 별점 없음 */
    fun onRatingChange(rating: Int) = updateSheet { it.copy(rating = if (it.rating == rating) null else rating.coerceIn(1, 5)) }

    fun onReviewChange(value: String) = updateSheet { it.copy(review = value.take(MAX_REVIEW)) }

    fun onShareToChatChange(value: Boolean) = updateSheet { it.copy(shareToChat = value) }

    fun share() {
        val state = _uiState.value
        val sheet = state.sheet ?: return
        val groupId = state.groupId ?: return
        if (sheet.saving) return
        updateSheet { it.copy(saving = true) }
        viewModelScope.launch {
            val result = placeRepository.share(
                groupId,
                PlaceShare(sheet.item.kakaoPlaceId, sheet.category, sheet.rating, sheet.review, sheet.shareToChat),
            )
            when {
                result is ApiResult.Success -> {
                    _uiState.update { it.copy(sheet = null) }
                    _events.send(PlaceSearchEvent.OpenPlace(result.value.id))
                }
                // 그 사이 다른 가족이 같은 장소를 공유했다 → 결과의 "공유됨"을 맞추려 다시 검색
                result.errorCode == ErrorCodes.PLACE_ALREADY_SHARED -> {
                    _uiState.update { it.copy(sheet = null) }
                    _events.send(PlaceSearchEvent.Message(UiText.Res(R.string.place_already_shared)))
                    searchNow()
                }
                // 검색한 지 1시간이 지나 서버 보관이 끝났다 → 다시 검색해서 고르게
                result is ApiResult.Failure && result.status == 400 -> {
                    _uiState.update { it.copy(sheet = null) }
                    _events.send(PlaceSearchEvent.Message(result.toUiText()))
                    searchNow()
                }
                else -> {
                    updateSheet { it.copy(saving = false) }
                    _events.send(PlaceSearchEvent.Message(result.toUiText()))
                }
            }
        }
    }

    private suspend fun search(query: String) {
        val groupId = _uiState.value.groupId ?: return
        _uiState.update { it.copy(searching = true, searchFailed = false, searchedQuery = query) }
        val result = placeRepository.search(query, 1, groupId)
        _uiState.update { current ->
            if (result is ApiResult.Success) {
                current.copy(results = result.value.items, page = 1, hasMore = result.value.hasMore, searching = false)
            } else {
                current.copy(results = emptyList(), page = 0, hasMore = false, searching = false, searchFailed = true)
            }
        }
        // 분당 횟수 제한 등 서버가 준 문구를 그대로 보인다
        if (result !is ApiResult.Success) _events.send(PlaceSearchEvent.Message(result.toUiText()))
    }

    private fun updateSheet(transform: (PlaceShareSheet) -> PlaceShareSheet) = _uiState.update { state ->
        state.copy(sheet = state.sheet?.let(transform))
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val MAX_PAGE = 45
        const val MAX_QUERY = 100

        /** 서버 한줄평 최대 길이 */
        const val MAX_REVIEW = 100
    }
}
