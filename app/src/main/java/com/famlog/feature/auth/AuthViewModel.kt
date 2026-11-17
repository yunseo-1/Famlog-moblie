package com.famlog.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.AgePolicy
import com.famlog.core.data.AuthRepository
import com.famlog.core.data.StartDestination
import com.famlog.core.data.StartResolver
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.SocialProvider
import com.famlog.core.model.errorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

enum class AuthStep { LOGIN, SIGNUP }

/** 출생연월 입력 상태. UNDER_AGE면 안내만 보이고 가입을 진행할 수 없다 (18.1 AUTH-02) */
enum class BirthCheck { EMPTY, INVALID, UNDER_AGE, OK }

data class AuthUiState(
    val step: AuthStep = AuthStep.LOGIN,
    val loadingProvider: SocialProvider? = null,
    val submitting: Boolean = false,
    val message: UiText? = null,
    val agreeTerms: Boolean = false,
    val agreePrivacy: Boolean = false,
    val nickname: String = "",
    val birthYm: String = "",
    val birthCheck: BirthCheck = BirthCheck.EMPTY,
) {
    val nicknameValid: Boolean get() = nickname.isNotBlank()
    val canSignup: Boolean
        get() = agreeTerms && agreePrivacy && nicknameValid && birthCheck == BirthCheck.OK && !submitting
}

/**
 * SCR-01 로그인 → (처음 온 계정이면) 가입 단계 (AUTH-01, AUTH-02).
 * 소셜 토큰은 가입 단계에서 다시 쓰기 위해 메모리에만 둔다 (화면 상태·저장소에 남기지 않는다).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val socialLoginClient: SocialLoginClient,
    private val authRepository: AuthRepository,
    private val startResolver: StartResolver,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _destinations = Channel<StartDestination>(Channel.BUFFERED)
    val destinations: Flow<StartDestination> = _destinations.receiveAsFlow()

    private var pendingSignup: Pair<SocialProvider, String>? = null

    fun login(provider: SocialProvider, activityContext: Context) {
        if (_state.value.loadingProvider != null) return
        _state.update { it.copy(loadingProvider = provider, message = null) }
        viewModelScope.launch {
            val token = when (val result = socialLoginClient.requestToken(provider, activityContext)) {
                is SocialLoginResult.Token -> result.value
                SocialLoginResult.Cancelled -> return@launch finishLoading(null)
                SocialLoginResult.NotConfigured ->
                    return@launch finishLoading(UiText.Res(R.string.login_error_not_configured))
                SocialLoginResult.Failed -> return@launch finishLoading(UiText.Res(R.string.login_error_social))
            }
            val result = authRepository.login(provider, token)
            when {
                result is ApiResult.Success -> _destinations.send(startResolver.resolve())
                result.errorCode == ErrorCodes.USER_NOT_REGISTERED -> {
                    pendingSignup = provider to token
                    _state.update { it.copy(step = AuthStep.SIGNUP) }
                }
                else -> finishLoading(result.toUiText())
            }
            _state.update { it.copy(loadingProvider = null) }
        }
    }

    private fun finishLoading(message: UiText?) {
        _state.update { it.copy(loadingProvider = null, message = message) }
    }

    fun backToLogin() {
        pendingSignup = null
        _state.value = AuthUiState()
    }

    fun setAgreeAll(agree: Boolean) = _state.update { it.copy(agreeTerms = agree, agreePrivacy = agree) }

    fun setAgreeTerms(agree: Boolean) = _state.update { it.copy(agreeTerms = agree) }

    fun setAgreePrivacy(agree: Boolean) = _state.update { it.copy(agreePrivacy = agree) }

    fun onNicknameChange(value: String) = _state.update { it.copy(nickname = value.take(NICKNAME_MAX)) }

    fun onBirthYmChange(value: String) {
        val digits = value.filter(Char::isDigit).take(6)
        _state.update { it.copy(birthYm = digits, birthCheck = checkBirth(digits), message = null) }
    }

    private fun checkBirth(birthYm: String): BirthCheck {
        if (birthYm.isEmpty()) return BirthCheck.EMPTY
        val now = YearMonth.now(clock)
        val birth = AgePolicy.parse(birthYm, now) ?: return BirthCheck.INVALID
        return if (AgePolicy.isOldEnough(birth, now)) BirthCheck.OK else BirthCheck.UNDER_AGE
    }

    fun signup() {
        val state = _state.value
        val (provider, token) = pendingSignup ?: return backToLogin()
        if (!state.canSignup) return
        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val result = authRepository.signup(provider, token, state.nickname.trim(), state.birthYm)
            if (result is ApiResult.Success) {
                pendingSignup = null
                _destinations.send(startResolver.resolve())
            } else {
                val message = if (result.errorCode == ErrorCodes.AGE_RESTRICTED) {
                    _state.update { it.copy(birthCheck = BirthCheck.UNDER_AGE) }
                    null // 출생연월 칸 아래 안내로 보인다
                } else {
                    result.toUiText()
                }
                _state.update { it.copy(message = message) }
            }
            _state.update { it.copy(submitting = false) }
        }
    }

    companion object {
        const val NICKNAME_MAX = 30
    }
}
