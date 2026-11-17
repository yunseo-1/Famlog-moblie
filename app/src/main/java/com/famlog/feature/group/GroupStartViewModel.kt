package com.famlog.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.StartDestination
import com.famlog.core.data.StartResolver
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
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

data class GroupStartUiState(
    val name: String = "",
    val code: String = "",
    val submitting: Boolean = false,
    val message: UiText? = null,
) {
    val canCreate: Boolean get() = name.isNotBlank() && !submitting
    val canJoin: Boolean get() = code.length == INVITE_CODE_LENGTH && !submitting
}

const val INVITE_CODE_LENGTH = 8

/** SCR-02 그룹 시작: 새 그룹 만들기(GRP-01) 또는 초대 코드로 참여(GRP-02) */
@HiltViewModel
class GroupStartViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val startResolver: StartResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupStartUiState())
    val state: StateFlow<GroupStartUiState> = _state.asStateFlow()

    private val _destinations = Channel<StartDestination>(Channel.BUFFERED)
    val destinations: Flow<StartDestination> = _destinations.receiveAsFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(GROUP_NAME_MAX), message = null) }

    /** 코드는 "K7MP-Q2XA"처럼 공유된다. 영문·숫자만 남기고 대문자로 맞춘다 (서버도 같은 정리를 한다) */
    fun onCodeChange(value: String) = _state.update {
        it.copy(code = normalizeInviteCode(value), message = null)
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun create() {
        if (!_state.value.canCreate) return
        submit { groupRepository.create(_state.value.name.trim()) }
    }

    fun join() {
        if (!_state.value.canJoin) return
        submit { groupRepository.join(_state.value.code) }
    }

    private fun submit(call: suspend () -> ApiResult<GroupDetail>) {
        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val result = call()
            if (result is ApiResult.Success) {
                _destinations.send(startResolver.afterJoin(result.value))
            } else {
                _state.update { it.copy(message = joinMessage(result)) }
            }
            _state.update { it.copy(submitting = false) }
        }
    }

    private fun joinMessage(result: ApiResult<*>): UiText = when ((result as? ApiResult.Failure)?.error?.code) {
        ErrorCodes.NOT_FOUND -> UiText.Res(R.string.join_error_not_found)
        ErrorCodes.INVITE_EXPIRED -> UiText.Res(R.string.join_error_expired)
        ErrorCodes.RATE_LIMITED -> UiText.Res(R.string.join_error_locked)
        else -> result.toUiText()
    }

    companion object {
        const val GROUP_NAME_MAX = 30
    }
}

fun normalizeInviteCode(value: String): String =
    value.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.uppercase().take(INVITE_CODE_LENGTH)
