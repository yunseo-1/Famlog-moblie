package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.core.data.CandidateRepository
import com.famlog.core.data.ChatRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateDetail
import com.famlog.core.model.CandidateFields
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.CandidateSurface
import com.famlog.core.model.CandidateType
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.Member
import com.famlog.core.model.MessageType
import com.famlog.core.model.SendState
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CandidateInboxViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-12T05:20:00Z"), ZoneId.of("Asia/Seoul"))
    private val mom = Member(MOM, "김엄마", GroupRole.MEMBER, null, "엄마", null)
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { groups } returns MutableStateFlow(listOf(GroupSummary(GROUP, "우리 가족", 2, GroupRole.MEMBER)))
        every { details } returns MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, listOf(mom))))
    }
    /** 근거 메시지 30은 폰에 받아 두었고, 12는 아직 없다 */
    private val chat = mockk<ChatRepository>(relaxed = true) {
        every { messages(GROUP) } returns MutableStateFlow(listOf(message(30)))
    }
    private val store = MutableStateFlow<List<Candidate>>(emptyList())
    private val hasMore = MutableStateFlow(false)
    private val candidates = mockk<CandidateRepository>(relaxed = true) {
        every { candidates(GROUP) } returns store
        every { inboxHasMore(GROUP) } returns hasMore
        coEvery { loadInbox(GROUP, any()) } returns ApiResult.Success(Unit)
    }

    private fun viewModel() = CandidateInboxViewModel(
        SavedStateHandle(mapOf("groupId" to GROUP)), candidates, chat, groups, mockk<PlaceRepository>(relaxed = true), clock,
    )

    private fun TestScope.started(): CandidateInboxViewModel = viewModel().also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun 신뢰도와_상관없이_만료_전_대기_후보를_최신_순으로_보인다() = runTest(mainRule.dispatcher) {
        store.value = listOf(
            todo(1, minute = 1, surface = CandidateSurface.CARD),
            todo(2, minute = 3, surface = CandidateSurface.INBOX),
            todo(3, minute = 2).copy(status = CandidateStatus.APPROVED),
            todo(4, minute = 4).copy(expiresAt = clock.instant().minusSeconds(1)),
        )

        val state = started().uiState.value

        coVerify { candidates.loadInbox(GROUP, false) }
        assertEquals(listOf(2L, 1L), state.items.map { it.id })
        assertFalse(state.loading)
    }

    @Test
    fun 근거_메시지를_받아_두었으면_인용하고_없으면_원문_보기만() = runTest(mainRule.dispatcher) {
        store.value = listOf(todo(1, minute = 1, anchor = 30), todo(2, minute = 2, anchor = 12))

        val state = started().uiState.value

        assertEquals("b30", state.anchors[30L]?.body)
        assertNull(state.anchors[12L])
    }

    @Test
    fun 끝에_닿으면_다음_쪽을_받고_더_없으면_묻지_않는다() = runTest(mainRule.dispatcher) {
        store.value = listOf(todo(1, minute = 1))
        hasMore.value = true
        val viewModel = started()

        viewModel.loadMore()
        coVerify(exactly = 1) { candidates.loadInbox(GROUP, true) }

        hasMore.value = false
        viewModel.loadMore()
        coVerify(exactly = 1) { candidates.loadInbox(GROUP, true) }
    }

    @Test
    fun 처음_못_받으면_실패를_보이고_다시_받는다() = runTest(mainRule.dispatcher) {
        coEvery { candidates.loadInbox(GROUP, false) } returns ApiResult.NetworkError andThen ApiResult.Success(Unit)
        val viewModel = started()
        assertTrue(viewModel.uiState.value.loadFailed)

        viewModel.refresh()

        assertFalse(viewModel.uiState.value.loadFailed)
    }

    @Test
    fun 등록하면_대기에서_빠지고_채울_칸이_있으면_시트를_연다() = runTest(mainRule.dispatcher) {
        val ok = todo(1, minute = 1)
        val missing = todo(2, minute = 2)
        store.value = listOf(ok, missing)
        coEvery { candidates.approve(ok, null) } coAnswers {
            store.update { list -> list.map { if (it.id == 1L) it.copy(status = CandidateStatus.APPROVED) else it } }
            ApiResult.Success(ok.copy(status = CandidateStatus.APPROVED))
        }
        coEvery { candidates.approve(missing, null) } returns
            ApiResult.Failure(422, ApiError(ErrorCodes.CANDIDATE_MISSING_FIELDS, "담당자를 골라 주세요.", null, listOf("assigneeIds")))
        coEvery { candidates.detail(2) } returns ApiResult.Success(CandidateDetail(missing, emptyList()))
        val viewModel = started()

        viewModel.actions.approve(ok)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })

        viewModel.actions.approve(missing)
        assertEquals(setOf("assigneeIds"), viewModel.uiState.value.sheet?.serverMissing)
    }

    @Test
    fun 무시하면_사유를_보내고_실패하면_서버_문구를_알린다() = runTest(mainRule.dispatcher) {
        val candidate = todo(1, minute = 1)
        store.value = listOf(candidate)
        coEvery { candidates.dismiss(candidate, null) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.CANDIDATE_ALREADY_DECIDED, "엄마님이 먼저 등록했어요.", null))
        val viewModel = started()

        viewModel.events.test {
            viewModel.actions.dismiss(candidate, null)
            assertEquals(CandidateEvent.Message(UiText.Plain("엄마님이 먼저 등록했어요.")), awaitItem())
        }
        coVerify { candidates.detail(1) }
    }

    private fun todo(id: Long, minute: Long, surface: CandidateSurface = CandidateSurface.INBOX, anchor: Long = 30) = Candidate(
        id = id, groupId = GROUP, type = CandidateType.TODO, status = CandidateStatus.PENDING,
        fields = CandidateFields.Todo("할 일 $id", LocalDate.of(2026, 11, 15), listOf(MOM), false, emptyList(), emptyList()),
        inferred = emptySet(), missingFields = emptySet(), confidence = 0.6, surface = surface, anchorMessageId = anchor,
        anchorSeq = anchor, version = 1, expiresAt = Instant.parse("2026-11-19T05:20:00Z"),
        createdAt = clock.instant().minusSeconds(600 - minute * 60),
    )

    private fun message(id: Long) = ChatMessage(
        key = "k$id", groupId = GROUP, id = id, seq = id, senderId = MOM, clientMessageId = null, type = MessageType.TEXT,
        body = "b$id", replyTo = null, reactions = emptyList(), unreadCount = 0, deleted = false, sentAt = clock.instant(),
        sendState = SendState.SENT,
    )

    private companion object {
        const val GROUP = 1L
        const val MOM = 2L
    }
}
