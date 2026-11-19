package com.famlog.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Member
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.RegionCounts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlaceListUiState(
    val groupId: Long? = null,
    val members: List<Member> = emptyList(),
    val filter: PlaceFilter = PlaceFilter(),
    val places: List<Place> = emptyList(),
    val nextCursor: String? = null,
    /** 지역 선택 시트의 개수 배지 (구성원·카테고리 필터 반영). 못 받았으면 null */
    val regions: RegionCounts? = null,
    /** 별칭 장소 수 (PLC-06, 리스트 위 진입 줄). 못 받았으면 null */
    val aliasCount: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loadFailed: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
) {
    fun nameOf(userId: Long?): String? = userId?.let { id -> members.firstOrNull { it.userId == id }?.displayName }
}

/**
 * SCR-31 장소 목록 (PLC-02·03). 현재 가족 그룹의 장소만.
 * 지역(시·도 → 시·군·구)·공유한 사람·카테고리 필터를 함께 걸고, 바꿀 때마다 서버에서 다시 받는다.
 */
@HiltViewModel
class PlaceListViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val groupRepository: GroupRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(PlaceListUiState(today = LocalDate.now(clock)))
    private var loadJob: Job? = null
    private var regionJob: Job? = null

    val uiState: StateFlow<PlaceListUiState> = combine(local, groupRepository.details) { state, details ->
        state.copy(members = state.groupId?.let { details[it] }?.members.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch {
            groupRepository.currentGroupId.filterNotNull().distinctUntilChanged().collect { groupId ->
                local.update { it.copy(groupId = groupId, places = emptyList(), regions = null, aliasCount = null, filter = PlaceFilter()) }
                launch { if (groupRepository.details.value[groupId] == null) groupRepository.detail(groupId) }
                load()
                loadRegions()
                loadAliasCount()
            }
        }
        // 공유·수정·삭제하고 돌아오면 다시 받는다
        viewModelScope.launch {
            placeRepository.changes.collect { groupId ->
                if (groupId == local.value.groupId) {
                    load()
                    loadRegions()
                    loadAliasCount()
                }
            }
        }
    }

    fun refresh() {
        if (local.value.groupId == null) return
        load()
        loadRegions()
        loadAliasCount()
    }

    /** [region1]이 null이면 전체 지역. [region2]가 null이면 시·도 전체 */
    fun selectRegion(region1: String?, region2: String?) =
        updateFilter(reloadRegions = false) { it.copy(region1 = region1, region2 = region2.takeIf { region1 != null }) }

    fun selectAllMembers() = updateFilter { it.copy(sharedBy = emptySet()) }

    fun toggleMember(userId: Long) = updateFilter { filter ->
        filter.copy(sharedBy = if (userId in filter.sharedBy) filter.sharedBy - userId else filter.sharedBy + userId)
    }

    fun selectAllCategories() = updateFilter { it.copy(categories = emptySet()) }

    fun toggleCategory(category: PlaceCategory) = updateFilter { filter ->
        filter.copy(categories = if (category in filter.categories) filter.categories - category else filter.categories + category)
    }

    /** 목록 끝에 가까워지면 다음 페이지 */
    fun loadMore() {
        val state = local.value
        val groupId = state.groupId ?: return
        val cursor = state.nextCursor ?: return
        if (state.loadingMore || state.loading) return
        local.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = placeRepository.page(groupId, state.filter, cursor)
            local.update { current ->
                if (result is ApiResult.Success && current.filter == state.filter) {
                    current.copy(
                        places = current.places + result.value.items.filter { new -> current.places.none { it.id == new.id } },
                        nextCursor = result.value.nextCursor,
                        loadingMore = false,
                    )
                } else {
                    current.copy(loadingMore = false)
                }
            }
        }
    }

    /** 지역 배지는 지역 필터를 받지 않으므로 지역만 바꿀 때는 다시 받지 않는다 */
    private fun updateFilter(reloadRegions: Boolean = true, transform: (PlaceFilter) -> PlaceFilter) {
        val before = local.value.filter
        val after = transform(before)
        if (after == before) return
        local.update { it.copy(filter = after) }
        load()
        if (reloadRegions) loadRegions()
    }

    private fun load() {
        val state = local.value
        val groupId = state.groupId ?: return
        loadJob?.cancel()
        local.update { it.copy(loading = true, loadFailed = false, nextCursor = null) }
        loadJob = viewModelScope.launch {
            val result = placeRepository.page(groupId, state.filter)
            local.update {
                if (result is ApiResult.Success) {
                    it.copy(places = result.value.items, nextCursor = result.value.nextCursor, loading = false, today = LocalDate.now(clock))
                } else {
                    it.copy(loading = false, loadFailed = true)
                }
            }
        }
    }

    private fun loadAliasCount() {
        val groupId = local.value.groupId ?: return
        viewModelScope.launch {
            val result = placeRepository.aliases(groupId)
            if (result is ApiResult.Success) local.update { if (it.groupId == groupId) it.copy(aliasCount = result.value.size) else it }
        }
    }

    private fun loadRegions() {
        val state = local.value
        val groupId = state.groupId ?: return
        regionJob?.cancel()
        regionJob = viewModelScope.launch {
            val result = placeRepository.regions(groupId, state.filter)
            if (result is ApiResult.Success) local.update { it.copy(regions = result.value) }
        }
    }
}
