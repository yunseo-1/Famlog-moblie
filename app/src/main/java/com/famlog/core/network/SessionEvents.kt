package com.famlog.core.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionEvent {
    /** 토큰이 무효(AUTH_INVALID)이거나 재발급에 실패해 다시 로그인해야 한다 */
    data object LoggedOut : SessionEvent
}

/** 네트워크 계층에서 생긴 세션 변화를 화면 쪽(내비게이션)에 알린다 */
@Singleton
class SessionEvents @Inject constructor() {
    private val _events = MutableSharedFlow<SessionEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun notifyLoggedOut() {
        _events.tryEmit(SessionEvent.LoggedOut)
    }
}
