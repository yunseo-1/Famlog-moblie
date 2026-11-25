package com.famlog.core.data

import com.famlog.core.common.NetworkMonitor
import com.famlog.core.datastore.FakeTokenStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.AuthTokens
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.network.ChatEventEnvelope
import com.famlog.core.network.SessionEvents
import com.famlog.core.network.SocketEvent
import com.famlog.testing.FakeChatSocket
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 연결 시점·재연결·토큰 만료 처리 (CHAT-02). 시간은 가상 시계로 흘린다 */
class ChatSyncTest {

    private val socket = FakeChatSocket()
    private val chat = mockk<ChatRepository>(relaxed = true)
    private val candidates = mockk<CandidateRepository>(relaxed = true)
    private val groupList = MutableStateFlow(listOf(GroupSummary(1, "우리 가족", 2, GroupRole.OWNER)))
    private val groups = mockk<GroupRepository>(relaxed = true) { every { groups } returns groupList }
    private val exports = mockk<DataExportRepository>(relaxed = true)
    private val tokens = FakeTokenStore(AuthTokens("access-1", "refresh-1"))
    private val sessionEvents = SessionEvents()
    private val network = MutableSharedFlow<Unit>()
    private val monitor = object : NetworkMonitor {
        override val onAvailable = network
    }

    private fun TestScope.start(): ChatSync =
        ChatSync(socket, chat, candidates, groups, exports, tokens, sessionEvents, monitor, backgroundScope).also {
            it.start()
            it.setForeground(true)
            runCurrent()
        }

    @Test
    fun 화면에_보이고_로그인돼_있으면_연결하고_내_그룹을_구독한다() = runTest {
        start()

        assertEquals(listOf("access-1"), socket.connectTokens)
        assertEquals(setOf(1L), socket.groups)
    }

    @Test
    fun 백그라운드로_가면_끊는다() = runTest {
        val sync = start()

        sync.setForeground(false)
        runCurrent()

        assertTrue(socket.disconnectCount > 0)
    }

    @Test
    fun 토큰_만료로_끊기면_REST로_재발급을_맡기고_새_토큰으로_바로_다시_연결한다() = runTest {
        coEvery { groups.refreshGroups() } coAnswers {
            tokens.save(AuthTokens("access-2", "refresh-2")) // TokenAuthenticator가 재발급한 셈
            ApiResult.Success(groupList.value)
        }
        start()

        socket.emit(SocketEvent.Closed(ErrorCodes.AUTH_TOKEN_EXPIRED))
        runCurrent()

        coVerify(exactly = 1) { groups.refreshGroups() }
        assertEquals(listOf("access-1", "access-2"), socket.connectTokens)
    }

    @Test
    fun 그냥_끊기면_1초_2초로_늘려가며_다시_연결하고_네트워크가_돌아오면_바로_시도한다() = runTest {
        start()

        socket.emit(SocketEvent.Closed(null))
        runCurrent()
        assertEquals(1, socket.connectTokens.size)
        advanceTimeBy(1_001)
        assertEquals(2, socket.connectTokens.size)

        socket.emit(SocketEvent.Closed(null))
        runCurrent()
        advanceTimeBy(1_500)
        assertEquals(2, socket.connectTokens.size)
        network.emit(Unit)
        runCurrent()
        assertEquals(3, socket.connectTokens.size)
    }

    @Test
    fun 연결되면_캐시가_있는_그룹을_채우고_대기열을_보낸다() = runTest {
        start()

        socket.emit(SocketEvent.Connected)
        runCurrent()

        coVerify { chat.syncCached(listOf(1L)) }
        coVerify { chat.flushPending(any()) }
    }

    @Test
    fun 로그아웃되면_연결을_끊고_채팅_캐시를_비운다() = runTest {
        start()
        val before = socket.disconnectCount

        sessionEvents.notifyLoggedOut()
        runCurrent()

        coVerify { chat.clear() }
        assertEquals(before + 1, socket.disconnectCount)
    }

    @Test
    fun 로그아웃_후_같은_계정으로_다시_로그인하면_다시_연결한다() = runTest {
        every { groups.clear() } answers { groupList.value = emptyList() }
        start()
        val group = groupList.value

        tokens.clear()
        sessionEvents.notifyLoggedOut()
        socket.emit(SocketEvent.Closed(null))
        advanceTimeBy(60_000) // 재연결 대기를 지나 토큰 없음으로 깨우기를 기다리는 상태
        tokens.save(AuthTokens("access-2", "refresh-2"))
        groupList.value = group // 로그인 직후 refreshGroups가 같은 목록을 받은 셈
        runCurrent()

        assertEquals("access-2", socket.connectTokens.last())
    }

    @Test
    fun 내보내기_준비_이벤트가_오면_내보내기_저장소에_알린다() = runTest {
        start()

        socket.emit(SocketEvent.User(ChatEventEnvelope("data-export.ready", buildJsonObject { put("id", 9) })))
        runCurrent()

        verify { exports.onReady(9) }
    }
}
