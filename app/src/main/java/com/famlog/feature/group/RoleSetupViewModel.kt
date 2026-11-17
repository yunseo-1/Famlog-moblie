package com.famlog.feature.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.GroupRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.FamilyRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleSetupUiState(
    val loading: Boolean = true,
    val groupName: String = "",
    val familyRole: FamilyRole? = null,
    val familyTitle: String = "",
    /** 서버가 가입할 때 자동으로 고른 색을 기본값으로 보여준다 */
    val calendarColor: String? = null,
    val saving: Boolean = false,
    val message: UiText? = null,
) {
    val canSave: Boolean get() = !loading && familyRole != null && !saving
}

/** SCR-03 내 가족 역할 설정 (GRP-04): 역할·호칭·캘린더 색. 그룹마다 따로 정한다 */
@HiltViewModel
class RoleSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    /** RoleSetupRoute.groupId (type-safe 내비게이션 인자는 속성 이름으로 들어온다) */
    private val groupId: Long = checkNotNull(savedStateHandle.get<Long>("groupId"))

    private val _state = MutableStateFlow(RoleSetupUiState())
    val state: StateFlow<RoleSetupUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            when (val result = groupRepository.detail(groupId)) {
                is ApiResult.Success -> {
                    val me = groupRepository.me(result.value)
                    _state.update {
                        it.copy(
                            loading = false,
                            groupName = result.value.name,
                            familyRole = me?.familyRole,
                            familyTitle = me?.familyTitle.orEmpty(),
                            calendarColor = me?.calendarColor?.uppercase(),
                        )
                    }
                }
                else -> _state.update { it.copy(loading = false, message = result.toUiText()) }
            }
        }
    }

    fun selectRole(role: FamilyRole) = _state.update { it.copy(familyRole = role) }

    fun onTitleChange(value: String) = _state.update { it.copy(familyTitle = value.take(FAMILY_TITLE_MAX)) }

    fun selectColor(hex: String) = _state.update { it.copy(calendarColor = hex.uppercase()) }

    fun save() {
        val state = _state.value
        val role = state.familyRole ?: return
        if (!state.canSave) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result = groupRepository.updateMyMember(
                groupId = groupId,
                familyRole = role,
                familyTitle = state.familyTitle.trim().ifEmpty { null },
                calendarColor = state.calendarColor,
            )
            if (result is ApiResult.Success) {
                _done.send(Unit)
            } else {
                _state.update { it.copy(message = result.toUiText()) }
            }
            _state.update { it.copy(saving = false) }
        }
    }

    companion object {
        const val FAMILY_TITLE_MAX = 20
    }
}
