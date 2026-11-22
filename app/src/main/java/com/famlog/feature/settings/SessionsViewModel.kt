package com.famlog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.SessionRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.LoginSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class SessionsUiState(
    val sessions: List<LoginSession> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** 로그아웃 확인 중인 기기 */
    val confirm: LoginSession? = null,
    val revoking: Boolean = false,
)

/** 로그인 기기 관리 (AUTH-03): 목록, 다른 기기 원격 로그아웃(확인 후). 이 기기 로그아웃은 설정의 [로그아웃] */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private val _events = Channel<UiText>(Channel.BUFFERED)
    val events: Flow<UiText> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val result = repository.sessions()
            _state.update {
                if (result is ApiResult.Success) it.copy(loading = false, sessions = result.value) else it.copy(loading = false, loadFailed = true)
            }
        }
    }

    fun ask(session: LoginSession) {
        if (session.current) return
        _state.update { it.copy(confirm = session) }
    }

    fun cancel() {
        if (!_state.value.revoking) _state.update { it.copy(confirm = null) }
    }

    fun revoke() {
        val target = _state.value.confirm ?: return
        if (_state.value.revoking) return
        _state.update { it.copy(revoking = true) }
        viewModelScope.launch {
            val result = repository.revoke(target.id)
            when {
                result is ApiResult.Success -> {
                    _state.update { s -> s.copy(revoking = false, confirm = null, sessions = s.sessions.filterNot { it.id == target.id }) }
                    _events.send(UiText.Res(R.string.sessions_revoked))
                }
                // 이미 없는 세션(만료·다른 곳에서 로그아웃) → 목록을 새로 받는다
                result is ApiResult.Failure && result.status == 404 -> {
                    _state.update { it.copy(revoking = false, confirm = null) }
                    load()
                }
                else -> {
                    _state.update { it.copy(revoking = false, confirm = null) }
                    _events.send(result.toUiText())
                }
            }
        }
    }
}

/** 마지막 접속: 방금 전 · n분 전 · n시간 전 · 그 이상은 날짜 */
fun lastUsedText(lastUsed: Instant?, now: Instant): UiText {
    lastUsed ?: return UiText.Res(R.string.sessions_last_unknown)
    val elapsed = Duration.between(lastUsed, now)
    return when {
        elapsed.toMinutes() < 1 -> UiText.Res(R.string.sessions_last_now)
        elapsed.toHours() < 1 -> UiText.Res(R.string.sessions_last_minutes, listOf(elapsed.toMinutes().toInt()))
        elapsed.toDays() < 1 -> UiText.Res(R.string.sessions_last_hours, listOf(elapsed.toHours().toInt()))
        else -> UiText.Plain(sessionDate.format(lastUsed))
    }
}

/** 처음 로그인한 날 */
fun sessionDay(instant: Instant): String = sessionDate.format(instant)

private val sessionDate = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))
