package com.famlog.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.ConsentRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.LocationSource
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Coordinates
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.MapBounds
import com.famlog.core.model.Member
import com.famlog.core.model.NearbyPlace
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.errorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** 위치기반서비스 약관 동의 창. [version]은 서버가 알려 준 현재 약관 버전 */
data class ConsentPrompt(val version: String, val saving: Boolean = false)

data class PlaceMapUiState(
    val groupId: Long? = null,
    val members: List<Member> = emptyList(),
    val categories: Set<PlaceCategory> = emptySet(),
    /** 처음 지도 가운데. 정해지기 전(null)에는 지도를 띄우지 않는다 */
    val initialCenter: Coordinates? = null,
    val bounds: MapBounds? = null,
    /** 지도 화면 영역 안 장소 (최근 공유 순) */
    val places: List<Place> = emptyList(),
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    /** 서버 기능 플래그가 꺼져 404가 나면 false → "내 주변" 버튼을 숨긴다 */
    val nearbyAvailable: Boolean = true,
    val nearbyLoading: Boolean = false,
    /** 내 주변 결과 (가까운 순). null이면 지도 영역 보기 */
    val nearby: List<NearbyPlace>? = null,
    /** 내 위치 점. 메모리에만 둔다 */
    val myLocation: Coordinates? = null,
    val consentPrompt: ConsentPrompt? = null,
    val today: LocalDate = LocalDate.MIN,
) {
    /** 내 주변 결과도 카테고리 칩으로 거른다 (서버 nearby는 카테고리 필터가 없다) */
    val nearbyVisible: List<NearbyPlace>?
        get() = nearby?.filter { categories.isEmpty() || it.place.category in categories }

    /** 지도 핀 */
    val pins: List<Place> get() = nearbyVisible?.map { it.place } ?: places

    fun nameOf(userId: Long?): String? = userId?.let { id -> members.firstOrNull { it.userId == id }?.displayName }
}

sealed interface PlaceMapEvent {
    data class Message(val text: UiText) : PlaceMapEvent

    /** 동의를 받았으니 대략적 위치 권한을 물어본다 (이미 있으면 바로 결과를 돌려준다) */
    data object RequestLocationPermission : PlaceMapEvent

    data class MoveCamera(val center: Coordinates) : PlaceMapEvent
}

/**
 * SCR-30 장소 탭 [지도] (PLC-04). 지도 이동이 멈추면 잠시 뒤 그 영역의 장소를 받는다.
 * 내 주변: 위치 약관 동의(서버) → 대략적 위치 권한 → 현재 위치 한 번 → nearby. 좌표는 저장·로그하지 않는다 (사양서 8.6).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class PlaceMapViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val consentRepository: ConsentRepository,
    private val locationSource: LocationSource,
    private val groupRepository: GroupRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(PlaceMapUiState(today = LocalDate.now(clock)))
    private val viewport = MutableStateFlow<MapBounds?>(null)
    private var loadJob: Job? = null

    val uiState: StateFlow<PlaceMapUiState> = combine(local, groupRepository.details) { state, details ->
        state.copy(members = state.groupId?.let { details[it] }?.members.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val _events = Channel<PlaceMapEvent>(Channel.BUFFERED)
    val events: Flow<PlaceMapEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            groupRepository.currentGroupId.filterNotNull().distinctUntilChanged().collect { groupId ->
                local.update {
                    PlaceMapUiState(groupId = groupId, nearbyAvailable = it.nearbyAvailable, today = LocalDate.now(clock))
                }
                launch { if (groupRepository.details.value[groupId] == null) groupRepository.detail(groupId) }
                pickInitialCenter(groupId)
            }
        }
        viewModelScope.launch {
            viewport.filterNotNull().debounce(VIEWPORT_DEBOUNCE_MS).distinctUntilChanged().collect { bounds ->
                local.update { it.copy(bounds = bounds) }
                load()
            }
        }
        // 공유·수정·삭제하고 돌아오면 다시 받는다
        viewModelScope.launch {
            placeRepository.changes.collect { groupId -> if (groupId == local.value.groupId) load() }
        }
    }

    /** 지도 이동이 멈출 때마다 (KakaoMap OnCameraMoveEnd) */
    fun onViewportChanged(bounds: MapBounds) {
        viewport.value = bounds
    }

    fun refresh() = load()

    fun selectAllCategories() = updateCategories(emptySet())

    fun toggleCategory(category: PlaceCategory) {
        val current = local.value.categories
        updateCategories(if (category in current) current - category else current + category)
    }

    /** [내 주변]: 지금 버전 약관에 동의했으면 권한으로, 아니면 약관 창 */
    fun startNearby() {
        if (local.value.nearbyLoading || local.value.consentPrompt != null) return
        viewModelScope.launch { checkConsent() }
    }

    fun dismissConsent() = local.update { it.copy(consentPrompt = null) }

    fun agreeLocation() {
        val prompt = local.value.consentPrompt ?: return
        if (prompt.saving) return
        local.update { it.copy(consentPrompt = prompt.copy(saving = true)) }
        viewModelScope.launch {
            val result = consentRepository.agreeLocation(prompt.version)
            when {
                result is ApiResult.Success -> {
                    local.update { it.copy(consentPrompt = null) }
                    _events.send(PlaceMapEvent.RequestLocationPermission)
                }
                // 그 사이 약관이 바뀌었다 → 최신 버전으로 다시 보여 준다
                result is ApiResult.Failure && result.status == 400 -> {
                    local.update { it.copy(consentPrompt = null) }
                    _events.send(PlaceMapEvent.Message(UiText.Res(R.string.place_consent_changed)))
                    checkConsent()
                }
                else -> {
                    local.update { it.copy(consentPrompt = prompt.copy(saving = false)) }
                    _events.send(PlaceMapEvent.Message(result.toUiText()))
                }
            }
        }
    }

    /** 위치 권한 요청 결과 (이미 있었으면 true) */
    fun onLocationPermission(granted: Boolean) {
        if (!granted) {
            _events.trySend(PlaceMapEvent.Message(UiText.Res(R.string.place_location_denied)))
            return
        }
        val groupId = local.value.groupId ?: return
        local.update { it.copy(nearbyLoading = true) }
        viewModelScope.launch {
            val at = locationSource.current()
            if (at == null) {
                local.update { it.copy(nearbyLoading = false) }
                _events.send(PlaceMapEvent.Message(UiText.Res(R.string.place_location_failed)))
                return@launch
            }
            val result = placeRepository.nearby(groupId, at)
            when {
                result is ApiResult.Success -> {
                    local.update { it.copy(nearbyLoading = false, nearby = result.value, myLocation = at) }
                    _events.send(PlaceMapEvent.MoveCamera(at))
                }
                // 서버 기능 플래그가 꺼졌다 (위치기반서비스 신고 전) → 버튼을 숨긴다
                result is ApiResult.Failure && result.status == 404 -> {
                    local.update { it.copy(nearbyLoading = false, nearbyAvailable = false) }
                    _events.send(PlaceMapEvent.Message(UiText.Res(R.string.place_nearby_unavailable)))
                }
                result.errorCode == ErrorCodes.LOCATION_CONSENT_REQUIRED -> {
                    local.update { it.copy(nearbyLoading = false) }
                    checkConsent()
                }
                else -> {
                    local.update { it.copy(nearbyLoading = false) }
                    _events.send(PlaceMapEvent.Message(result.toUiText()))
                }
            }
        }
    }

    /** 내 주변을 닫고 지도 영역 보기로 */
    fun closeNearby() = local.update { it.copy(nearby = null, myLocation = null) }

    private suspend fun checkConsent() {
        when (val result = consentRepository.location()) {
            is ApiResult.Success -> {
                val consent = result.value
                val version = consent.currentVersion
                when {
                    consent.valid -> _events.send(PlaceMapEvent.RequestLocationPermission)
                    version == null -> _events.send(PlaceMapEvent.Message(UiText.Res(R.string.place_nearby_unavailable)))
                    else -> local.update { it.copy(consentPrompt = ConsentPrompt(version)) }
                }
            }
            else -> _events.send(PlaceMapEvent.Message(result.toUiText()))
        }
    }

    private fun updateCategories(categories: Set<PlaceCategory>) {
        if (categories == local.value.categories) return
        local.update { it.copy(categories = categories) }
        load()
    }

    /** 가장 최근에 공유한 장소에서 시작한다. 없으면 서울시청 */
    private suspend fun pickInitialCenter(groupId: Long) {
        val latest = (placeRepository.page(groupId, PlaceFilter()) as? ApiResult.Success)?.value?.items?.firstOrNull()
        val center = latest?.let { Coordinates(it.lat, it.lng) } ?: DEFAULT_CENTER
        local.update { if (it.groupId == groupId) it.copy(initialCenter = center) else it }
    }

    private fun load() {
        val state = local.value
        val groupId = state.groupId ?: return
        val bounds = state.bounds ?: return
        loadJob?.cancel()
        local.update { it.copy(loading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            val result = placeRepository.page(groupId, PlaceFilter(categories = state.categories, bounds = bounds))
            local.update {
                if (result is ApiResult.Success) {
                    it.copy(places = result.value.items, loading = false)
                } else {
                    it.copy(loading = false, loadFailed = true)
                }
            }
        }
    }

    internal companion object {
        const val VIEWPORT_DEBOUNCE_MS = 300L

        /** 서울시청 */
        val DEFAULT_CENTER = Coordinates(37.5666, 126.9784)
    }
}
