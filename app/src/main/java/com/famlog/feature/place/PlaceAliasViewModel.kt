package com.famlog.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupRole
import com.famlog.core.model.PlaceAlias
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.PlaceSummary
import com.famlog.core.model.errorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 별칭 추가 시트. 장소 연결과 주소 글은 둘 다 선택 */
data class AliasSheet(
    val alias: String = "",
    val place: PlaceSummary? = null,
    val addressText: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    /** 같은 별칭이 이미 있다 (409) */
    val duplicated: Boolean = false,
) {
    val aliasMissing: Boolean get() = alias.isBlank()
}

data class PlaceAliasUiState(
    val groupId: Long? = null,
    val myUserId: Long? = null,
    val myRole: GroupRole = GroupRole.MEMBER,
    val aliases: List<PlaceAlias> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val sheet: AliasSheet? = null,
    val placePicker: PlacePicker? = null,
) {
    /** 삭제는 만든 사람 또는 관리자 이상 (서버와 같은 기준) */
    fun canDelete(alias: PlaceAlias): Boolean = alias.createdBy == myUserId || myRole != GroupRole.MEMBER
}

sealed interface PlaceAliasEvent {
    data class Message(val text: UiText) : PlaceAliasEvent
}

/** 별칭 장소 (PLC-06). "할머니 댁"처럼 검색이 안 되는 곳을 현재 가족 그룹 안에서만 쓰는 이름으로 저장한다 */
@HiltViewModel
class PlaceAliasViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val local = MutableStateFlow(PlaceAliasUiState())

    val uiState: StateFlow<PlaceAliasUiState> = combine(local, groupRepository.details) { state, details ->
        state.copy(myRole = state.groupId?.let { details[it] }?.myRole ?: GroupRole.MEMBER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val _events = Channel<PlaceAliasEvent>(Channel.BUFFERED)
    val events: Flow<PlaceAliasEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val groupId = groupRepository.currentGroupId.filterNotNull().first()
            local.update { it.copy(groupId = groupId, myUserId = sessionStore.userId()) }
            if (groupRepository.details.value[groupId] == null) launch { groupRepository.detail(groupId) }
            load()
        }
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    fun openSheet() = local.update { it.copy(sheet = AliasSheet()) }

    fun closeSheet() = local.update { it.copy(sheet = null, placePicker = null) }

    fun onAliasChange(value: String) = updateSheet { it.copy(alias = value.take(MAX_ALIAS), duplicated = false) }

    fun onAddressChange(value: String) = updateSheet { it.copy(addressText = value.take(MAX_ADDRESS)) }

    fun openPlacePicker() {
        val groupId = local.value.groupId ?: return
        local.update { it.copy(placePicker = PlacePicker(loading = true)) }
        viewModelScope.launch {
            val result = placeRepository.page(groupId, PlaceFilter())
            local.update { state ->
                if (state.placePicker == null) return@update state
                if (result is ApiResult.Success) {
                    state.copy(placePicker = PlacePicker(places = result.value.items.map { PlaceSummary(it.id, it.name, it.address, it.category) }))
                } else {
                    state.copy(placePicker = PlacePicker(failed = true))
                }
            }
        }
    }

    fun closePlacePicker() = local.update { it.copy(placePicker = null) }

    /** null이면 연결 빼기 */
    fun onPlaceChange(place: PlaceSummary?) = local.update { state ->
        state.copy(sheet = state.sheet?.copy(place = place), placePicker = null)
    }

    fun save() {
        val state = local.value
        val groupId = state.groupId ?: return
        val sheet = state.sheet ?: return
        if (sheet.saving) return
        if (sheet.aliasMissing) {
            updateSheet { it.copy(showErrors = true) }
            return
        }
        updateSheet { it.copy(saving = true) }
        viewModelScope.launch {
            val result = placeRepository.createAlias(groupId, sheet.alias, sheet.place?.id, sheet.addressText)
            when {
                result is ApiResult.Success -> {
                    local.update { it.copy(sheet = null) }
                    load()
                }
                result.errorCode == ErrorCodes.ALIAS_DUPLICATED -> updateSheet { it.copy(saving = false, duplicated = true) }
                else -> {
                    updateSheet { it.copy(saving = false) }
                    _events.send(PlaceAliasEvent.Message(result.toUiText()))
                }
            }
        }
    }

    fun delete(alias: PlaceAlias) {
        val groupId = local.value.groupId ?: return
        viewModelScope.launch {
            when (val result = placeRepository.deleteAlias(groupId, alias)) {
                is ApiResult.Success -> local.update { state -> state.copy(aliases = state.aliases.filterNot { it.id == alias.id }) }
                else -> _events.send(PlaceAliasEvent.Message(result.toUiText()))
            }
        }
    }

    private suspend fun load() {
        val groupId = local.value.groupId ?: return
        local.update { it.copy(loadFailed = false) }
        when (val result = placeRepository.aliases(groupId)) {
            is ApiResult.Success -> local.update { it.copy(aliases = result.value, loading = false) }
            else -> {
                local.update { it.copy(loading = false, loadFailed = true) }
                _events.send(PlaceAliasEvent.Message(UiText.Res(R.string.place_alias_load_failed)))
            }
        }
    }

    private fun updateSheet(transform: (AliasSheet) -> AliasSheet) = local.update { state ->
        state.copy(sheet = state.sheet?.let(transform))
    }

    private companion object {
        /** 서버 별칭 1~30자, 주소 글 200자 */
        const val MAX_ALIAS = 30
        const val MAX_ADDRESS = 200
    }
}
