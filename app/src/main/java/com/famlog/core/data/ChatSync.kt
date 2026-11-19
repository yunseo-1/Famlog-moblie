package com.famlog.core.data

import com.famlog.core.common.ApplicationScope
import com.famlog.core.common.NetworkMonitor
import com.famlog.core.datastore.TokenStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.network.ChatEventTypes
import com.famlog.core.network.ChatSocket
import com.famlog.core.network.SessionEvent
import com.famlog.core.network.SessionEvents
import com.famlog.core.network.SocketEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 실시간 채팅 연결을 관리한다 (CHAT-02).
 * - 앱이 화면에 보이고([setForeground]) 로그인돼 있으며 그룹이 있을 때만 연결한다.
 * - 끊기면 1, 2, 4 … 최대 [MAX_BACKOFF_MS] 간격으로 다시 연결한다. 네트워크가 돌아오면 바로 시도한다. 연결되면 놓친 메시지를 채우고 대기열을 보낸다.
 * - 서버가 토큰 문제로 끊으면(ERROR 프레임) 인증이 필요한 REST 요청을 한 번 보내 [com.famlog.core.network.TokenAuthenticator]가
 *   재발급하게 한다. 재발급 경로를 따로 만들지 않는다 (겹친 재발급은 서버가 재사용으로 보고 모든 기기를 로그아웃시킨다).
 */
@Singleton
class ChatSync @Inject constructor(
    private val socket: ChatSocket,
    private val chatRepository: ChatRepository,
    private val candidateRepository: CandidateRepository,
    private val groupRepository: GroupRepository,
    private val tokenStore: TokenStore,
    private val sessionEvents: SessionEvents,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val foreground = MutableStateFlow(false)

    /** 그룹 목록이 바뀌었다(로그인 직후 포함) → 기다리던 연결 시도를 깨운다 */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var started = false

    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            groupRepository.groups.collect { groups ->
                socket.setGroups(groups.map { it.id }.toSet())
                wake.trySend(Unit)
            }
        }
        scope.launch { socket.events.collect(::handle) }
        // 네트워크가 돌아오면 다시 연결 대기(최대 30초)를 끊고 바로 시도한다
        scope.launch { networkMonitor.onAvailable.collect { wake.trySend(Unit) } }
        scope.launch {
            sessionEvents.events.collect { event ->
                if (event == SessionEvent.LoggedOut) {
                    socket.disconnect()
                    chatRepository.clear()
                    candidateRepository.clear()
                }
            }
        }
        scope.launch {
            foreground.collectLatest { visible ->
                if (visible) {
                    try {
                        keepConnected()
                    } finally {
                        socket.disconnect()
                    }
                } else {
                    socket.disconnect()
                }
            }
        }
    }

    private suspend fun keepConnected() {
        var failures = 0
        while (true) {
            val token = tokenStore.get()?.accessToken
            if (token == null || groupRepository.groups.value.isEmpty()) {
                wake.receive()
                continue
            }
            // 연결 전에 쌓인 깨우기 신호는 버린다 (끊긴 뒤의 신호만 대기를 끊어야 한다)
            wake.tryReceive()
            val (wasConnected, closed) = connectOnce(token)
            failures = if (wasConnected) 0 else failures + 1
            if (closed.errorCode != null) {
                // 토큰 만료·무효: REST 한 번으로 재발급(또는 로그아웃)을 맡긴다. 구독 거부(그룹에서 나감)는 목록을 새로 받는다
                val result = groupRepository.refreshGroups()
                // 로그아웃됐으면 토큰이 없어 다음 바퀴에서 기다린다. 재발급에 성공했으면 바로 다시 연결한다
                if (result is ApiResult.Failure && result.status == 401) continue
                if (closed.errorCode == ErrorCodes.AUTH_TOKEN_EXPIRED && result is ApiResult.Success && failures <= 1) {
                    continue
                }
            }
            val backoff = (INITIAL_BACKOFF_MS shl (failures - 1).coerceIn(0, 5)).coerceAtMost(MAX_BACKOFF_MS)
            withTimeoutOrNull(backoff) { wake.receive() }
        }
    }

    /** 한 번 연결하고 끊길 때까지 기다린다. 연결 전에 이벤트를 구독해야 Closed를 놓치지 않는다 */
    private suspend fun connectOnce(token: String): Pair<Boolean, SocketEvent.Closed> = coroutineScope {
        var connected = false
        val closed = async(start = CoroutineStart.UNDISPATCHED) {
            socket.events.first { event ->
                if (event is SocketEvent.Connected) connected = true
                event is SocketEvent.Closed
            } as SocketEvent.Closed
        }
        socket.connect(token)
        val event = closed.await()
        connected to event
    }

    private suspend fun handle(event: SocketEvent) {
        when (event) {
            SocketEvent.Connected -> {
                chatRepository.syncCached(groupRepository.groups.value.map { it.id })
                flushPending()
            }
            is SocketEvent.Group -> {
                chatRepository.onGroupEvent(event.groupId, event.event)
                candidateRepository.onGroupEvent(event.groupId, event.event)
            }
            is SocketEvent.User -> when (event.event.type) {
                // 다른 기기에서 이 세션을 로그아웃시켰다 → REST가 401 AUTH_INVALID를 받아 로그인 화면으로 간다
                ChatEventTypes.SESSION_REVOKED -> groupRepository.refreshGroups()
                else -> if (chatRepository.onUserEvent(event.event)) {
                    scope.launch {
                        delay(RATE_LIMIT_RETRY_MS)
                        flushPending()
                    }
                }
            }
            is SocketEvent.Closed -> Unit
        }
    }

    private suspend fun flushPending() = chatRepository.flushPending { delay(SEND_INTERVAL_MS) }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val SEND_INTERVAL_MS = 250L
        const val RATE_LIMIT_RETRY_MS = 1_500L
    }
}
