package com.famlog.testing

import com.famlog.core.network.ChatSocket
import com.famlog.core.network.SendMessageRequest
import com.famlog.core.network.SocketEvent
import com.famlog.core.network.SocketState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 보낸 프레임을 기록하고, 테스트가 이벤트를 직접 흘려 넣는다 */
class FakeChatSocket(var connected: Boolean = true) : ChatSocket {
    val sent = mutableListOf<Pair<Long, SendMessageRequest>>()
    val reads = mutableListOf<Pair<Long, Long>>()
    val connectTokens = mutableListOf<String>()
    var groups: Set<Long> = emptySet()
        private set
    var disconnectCount = 0
        private set

    override val state: StateFlow<SocketState> = MutableStateFlow(SocketState.DISCONNECTED)
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SocketEvent> = _events

    suspend fun emit(event: SocketEvent) = _events.emit(event)

    override fun connect(accessToken: String) {
        connectTokens += accessToken
    }

    override fun disconnect() {
        disconnectCount++
    }

    override fun setGroups(groupIds: Set<Long>) {
        groups = groupIds
    }

    override fun send(groupId: Long, request: SendMessageRequest): Boolean {
        if (!connected) return false
        sent += groupId to request
        return true
    }

    override fun sendRead(groupId: Long, lastReadSeq: Long): Boolean {
        if (!connected) return false
        reads += groupId to lastReadSeq
        return true
    }
}
