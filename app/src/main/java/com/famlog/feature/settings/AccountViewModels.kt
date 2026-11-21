package com.famlog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.AuthRepository
import com.famlog.core.data.ConsentRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Consent
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.errorCode
import com.famlog.core.network.ApiBaseUrl
import com.famlog.core.network.AppVersion
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

/** 설정 (SCR-80): 개인정보·보안으로 가는 길, 로그아웃, 앱 버전 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @AppVersion val appVersion: String,
) : ViewModel() {

    private val _confirmLogout = MutableStateFlow(false)
    val confirmLogout: StateFlow<Boolean> = _confirmLogout.asStateFlow()

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    fun askLogout() {
        _confirmLogout.value = true
    }

    fun cancelLogout() {
        if (!_loggingOut.value) _confirmLogout.value = false
    }

    /** 이 기기만 로그아웃. 끝나면 로그인 화면으로 간다 (SessionEvent.LoggedOut) */
    fun logout() {
        if (_loggingOut.value) return
        _loggingOut.value = true
        viewModelScope.launch {
            authRepository.logout()
            _loggingOut.value = false
            _confirmLogout.value = false
        }
    }
}

/** 철회 확인 중인 동의 (위치기반서비스·AI 분석 안내) */
data class PrivacyUiState(
    val consents: List<Consent> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val withdraw: String? = null,
    val withdrawing: Boolean = false,
)

/** 개인정보·보안 (SCR-81): 동의 내역과 철회, 웹 삭제 안내, 계정 삭제로 가는 길 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val consentRepository: ConsentRepository,
    @ApiBaseUrl baseUrl: String,
) : ViewModel() {

    /** 웹 계정 삭제 안내 (WEB-01, Google Play 요건). API 주소의 서버 루트 */
    val webDeletionUrl: String = baseUrl.substringBefore("api/v1/") + "account-deletion"

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    private val _events = Channel<UiText>(Channel.BUFFERED)
    val events: Flow<UiText> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val result = consentRepository.all()
            _state.update {
                it.copy(
                    loading = false,
                    loadFailed = result !is ApiResult.Success,
                    consents = (result as? ApiResult.Success)?.value?.filter { c -> c.type in SHOWN }?.sortedBy { c -> SHOWN.indexOf(c.type) } ?: it.consents,
                )
            }
        }
    }

    fun askWithdraw(type: String) {
        if (type !in WITHDRAWABLE) return
        _state.update { it.copy(withdraw = type) }
    }

    fun cancelWithdraw() {
        if (!_state.value.withdrawing) _state.update { it.copy(withdraw = null) }
    }

    fun confirmWithdraw() {
        val type = _state.value.withdraw ?: return
        if (_state.value.withdrawing) return
        _state.update { it.copy(withdrawing = true) }
        viewModelScope.launch {
            val result = when (type) {
                Consent.LOCATION -> consentRepository.withdrawLocation()
                else -> consentRepository.withdrawAiNotice()
            }
            _state.update { it.copy(withdrawing = false, withdraw = null) }
            if (result is ApiResult.Success) {
                _events.send(UiText.Res(R.string.privacy_withdrawn))
                load()
            } else {
                _events.send(result.toUiText())
            }
        }
    }

    companion object {
        /** 화면에 보이는 순서. 모르는 종류는 보이지 않는다 */
        val SHOWN = listOf(Consent.TERMS, Consent.PRIVACY, Consent.LOCATION, Consent.AI_ANALYSIS, Consent.OVERSEAS_TRANSFER)

        /** 앱에서 철회할 수 있는 것 (국외 이전 고지는 AI 분석과 함께 철회된다) */
        val WITHDRAWABLE = setOf(Consent.LOCATION, Consent.AI_ANALYSIS)
    }
}

data class AccountDeleteUiState(
    val deleteMedia: Boolean = false,
    val confirm: Boolean = false,
    val deleting: Boolean = false,
    /** 다른 가족이 있는 그룹의 그룹장이라 지울 수 없다 (서버 문구, 그룹 이름 포함) */
    val ownerTransfer: UiText? = null,
)

/** 계정 삭제 (ACC-01): 안내 → 사진 지우기 선택 → 최종 확인. 성공하면 모든 기기 로그아웃(로그인 화면으로) */
@HiltViewModel
class AccountDeleteViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountDeleteUiState())
    val state: StateFlow<AccountDeleteUiState> = _state.asStateFlow()

    private val _events = Channel<UiText>(Channel.BUFFERED)
    val events: Flow<UiText> = _events.receiveAsFlow()

    fun setDeleteMedia(value: Boolean) = _state.update { it.copy(deleteMedia = value) }

    fun ask() = _state.update { it.copy(confirm = true) }

    fun cancel() {
        if (!_state.value.deleting) _state.update { it.copy(confirm = false) }
    }

    fun dismissOwnerTransfer() = _state.update { it.copy(ownerTransfer = null) }

    fun delete() {
        val current = _state.value
        if (current.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val result = authRepository.deleteAccount(current.deleteMedia)
            when {
                result is ApiResult.Success -> _state.update { it.copy(deleting = false, confirm = false) }
                result.errorCode == ErrorCodes.OWNER_TRANSFER_REQUIRED ->
                    _state.update { it.copy(deleting = false, confirm = false, ownerTransfer = result.toUiText()) }
                else -> {
                    _state.update { it.copy(deleting = false, confirm = false) }
                    _events.send(result.toUiText())
                }
            }
        }
    }
}
