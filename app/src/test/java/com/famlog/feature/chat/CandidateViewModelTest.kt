package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.CandidateRepository
import com.famlog.core.data.ChatRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateApproval
import com.famlog.core.model.CandidateDetail
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateEvidence
import com.famlog.core.model.CandidateFields
import com.famlog.core.model.CandidateLocation
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.CandidateSurface
import com.famlog.core.model.CandidateType
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.DismissReason
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.Member
import com.famlog.core.model.MessageType
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceOption
import com.famlog.core.model.PlaceSearchItem
import com.famlog.core.model.PlaceSearchPage
import com.famlog.core.model.SendState
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CandidateViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    /** 기준: 2026-11-12(목) 14:20 서울 */
    private val clock = MutableClock(Instant.parse("2026-11-12T05:20:00Z"))

    private val me = Member(ME, "윤서", GroupRole.MEMBER, null, "딸", null)
    private val mom = Member(MOM, "김엄마", GroupRole.MEMBER, null, "엄마", null)
    private val dad = Member(DAD, "김아빠", GroupRole.MEMBER, null, "아빠", null)
    private val groupList = MutableStateFlow(listOf(GroupSummary(GROUP, "우리 가족", 3, GroupRole.MEMBER)))
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { groups } returns groupList
        every { details } returns MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, listOf(me, mom, dad))))
    }

    private val messages = MutableStateFlow(listOf(message(10), message(30)))
    private val chat = mockk<ChatRepository>(relaxed = true) { every { messages(GROUP) } returns messages }

    private val store = MutableStateFlow<List<Candidate>>(emptyList())
    private val candidates = mockk<CandidateRepository>(relaxed = true) {
        every { candidates(GROUP) } returns store
        coEvery { loadForChat(any(), any(), any()) } returns ApiResult.Success(Unit)
    }
    private val places = mockk<PlaceRepository>(relaxed = true)

    private fun viewModel() = CandidateViewModel(
        SavedStateHandle(mapOf("groupId" to GROUP)), candidates, chat, groups, places, FakeSessionStore(userId = ME), clock,
    )

    private fun TestScope.started(): CandidateViewModel = viewModel().also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun 채팅방에는_신뢰도_높은_대기_후보와_승인한_후보만_근거_메시지_아래에_붙는다() = runTest(mainRule.dispatcher) {
        val card = schedule(1)
        val inbox = schedule(2).copy(surface = CandidateSurface.INBOX)
        val expired = schedule(3).copy(expiresAt = clock.instant().minusSeconds(1))
        val approvedInbox = schedule(4).copy(surface = CandidateSurface.INBOX, status = CandidateStatus.APPROVED, decidedBy = MOM)
        val dismissed = schedule(5).copy(status = CandidateStatus.DISMISSED)
        store.value = listOf(card, inbox, expired, approvedInbox, dismissed)

        val state = started().uiState.value

        assertEquals(listOf(1L, 4L), state.byAnchor.getValue(ANCHOR).map { it.id })
    }

    @Test
    fun 받아_둔_가장_오래된_메시지부터_받고_이전_메시지를_더_받으면_그_범위만_받는다() = runTest(mainRule.dispatcher) {
        started()
        coVerify { candidates.loadForChat(GROUP, 10, null) }

        messages.value = listOf(message(4), message(10), message(30))
        coVerify { candidates.loadForChat(GROUP, 4, 9) }

        // 새 메시지가 붙는 것만으로는 다시 받지 않는다 (새 후보는 실시간 이벤트로 온다)
        messages.value = listOf(message(4), message(10), message(30), message(31))
        coVerify(exactly = 2) { candidates.loadForChat(any(), any(), any()) }
    }

    @Test
    fun 등록은_값_없이_보내고_처리_중에는_다시_보내지_않는다() = runTest(mainRule.dispatcher) {
        val candidate = schedule(1)
        coEvery { candidates.approve(candidate, null) } returns ApiResult.Success(candidate.copy(status = CandidateStatus.APPROVED))
        val viewModel = started()

        viewModel.approve(candidate)

        coVerify(exactly = 1) { candidates.approve(candidate, null) }
        assertTrue(viewModel.uiState.value.busy.isEmpty())
        assertNull(viewModel.uiState.value.sheet)
    }

    @Test
    fun 채울_항목이_있어_422면_수정_시트를_열고_서버가_알린_칸을_강조한다() = runTest(mainRule.dispatcher) {
        val candidate = todo(choices = listOf(listOf(MOM, DAD)))
        coEvery { candidates.approve(candidate, null) } returns
            ApiResult.Failure(422, ApiError(ErrorCodes.CANDIDATE_MISSING_FIELDS, "담당자를 골라 주세요.", null, listOf("assigneeIds")))
        coEvery { candidates.detail(candidate.id) } returns ApiResult.Success(CandidateDetail(candidate, listOf(evidence(MOM))))
        val viewModel = started()

        viewModel.approve(candidate)

        val sheet = viewModel.uiState.value.sheet!!
        assertEquals(setOf("assigneeIds"), sheet.serverMissing)
        val form = sheet.form as CandidateForm.Todo
        // 같은 역할이 여러 명이면 비워 두고 고르게 한다
        assertNull(form.assigneeId)
        assertEquals(LocalDate.of(2026, 11, 15), form.dueDate)
        assertTrue(sheet.canSubmit)
    }

    @Test
    fun 다른_가족이_먼저_처리했으면_서버_문구를_보이고_최신_상태를_받는다() = runTest(mainRule.dispatcher) {
        val candidate = schedule(1)
        coEvery { candidates.approve(candidate, null) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.CANDIDATE_ALREADY_DECIDED, "엄마님이 먼저 등록했어요.", null))
        val viewModel = started()

        viewModel.events.test {
            viewModel.approve(candidate)
            assertEquals(CandidateEvent.Message(UiText.Plain("엄마님이 먼저 등록했어요.")), awaitItem())
        }
        coVerify { candidates.detail(candidate.id) }
    }

    @Test
    fun 그새_바뀌어_버전이_다르면_다시_확인하라고_하고_최신_상태를_받는다() = runTest(mainRule.dispatcher) {
        val candidate = schedule(1)
        coEvery { candidates.dismiss(candidate, DismissReason.WRONG) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.VERSION_CONFLICT, "충돌", null))
        val viewModel = started()

        viewModel.events.test {
            viewModel.dismiss(candidate, DismissReason.WRONG)
            assertEquals(CandidateEvent.Message(UiText.Res(R.string.candidate_changed)), awaitItem())
        }
        coVerify { candidates.dismiss(candidate, DismissReason.WRONG) }
        coVerify { candidates.detail(candidate.id) }
    }

    @Test
    fun 되돌리기는_승인한_사람에게_5분_동안만_보이고_관리자는_다른_사람_것도_보인다() = runTest(mainRule.dispatcher) {
        val mine = schedule(1).copy(status = CandidateStatus.APPROVED, decidedBy = ME, decidedAt = clock.instant())
        val moms = schedule(2).copy(status = CandidateStatus.APPROVED, decidedBy = MOM, decidedAt = clock.instant())
        store.value = listOf(mine, moms)
        val viewModel = started()

        assertEquals(setOf(1L), viewModel.uiState.value.undoable)

        groupList.value = listOf(GroupSummary(GROUP, "우리 가족", 3, GroupRole.ADMIN))
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.undoable)

        clock.now = clock.now.plus(Duration.ofMinutes(5))
        advanceTimeBy(CandidateViewModel.TICK_MS + 1)
        assertTrue(viewModel.uiState.value.undoable.isEmpty())
    }

    @Test
    fun 시트에서_12시로_바꿔_등록하면_고친_값으로_보낸다() = runTest(mainRule.dispatcher) {
        val candidate = schedule(1)
        coEvery { candidates.detail(candidate.id) } returns ApiResult.Success(CandidateDetail(candidate, listOf(evidence(MOM))))
        val approval = slot<CandidateApproval>()
        coEvery { candidates.approve(candidate, capture(approval)) } returns
            ApiResult.Success(candidate.copy(status = CandidateStatus.APPROVED))
        val viewModel = started()

        viewModel.edit(candidate)
        val form = viewModel.uiState.value.sheet!!.form as CandidateForm.Schedule
        // 가족 전체(추정)는 지금 가족 모두로 채운다
        assertEquals(setOf(ME, MOM, DAD), form.participantIds)
        viewModel.onStartTimeChange(LocalTime.NOON)
        viewModel.toggleParticipant(DAD)
        viewModel.submitSheet()

        val draft = (approval.captured as CandidateApproval.Schedule).draft
        assertEquals(Instant.parse("2026-11-14T03:00:00Z"), draft.startsAt)
        assertEquals(Instant.parse("2026-11-14T03:00:00Z"), draft.endsAt)
        assertEquals(setOf(ME, MOM), draft.participantIds.toSet())
        // 별칭에 연결된 장소가 없으면 메모로 남긴다 (서버 기본값과 같다)
        assertEquals("장소: 할머니 댁", draft.memo)
        assertNull(draft.placeId)
        assertNull(viewModel.uiState.value.sheet)
    }

    @Test
    fun 필수_항목이_비면_등록할_수_없다() = runTest(mainRule.dispatcher) {
        val candidate = schedule(1).let { it.copy(fields = (it.fields as CandidateFields.Schedule).copy(date = null)) }
        coEvery { candidates.detail(candidate.id) } returns ApiResult.Success(CandidateDetail(candidate, emptyList()))
        val viewModel = started()

        viewModel.edit(candidate)
        viewModel.onTitleChange("   ")
        viewModel.submitSheet()

        val sheet = viewModel.uiState.value.sheet!!
        assertEquals(setOf("title", "date"), sheet.errors)
        assertFalse(sheet.canSubmit)
        coVerify(exactly = 0) { candidates.approve(any(), any()) }
    }

    @Test
    fun 장소는_찾은_곳_중_고르거나_다시_검색해서_고른다() = runTest(mainRule.dispatcher) {
        val candidate = place()
        coEvery { candidates.detail(candidate.id) } returns ApiResult.Success(CandidateDetail(candidate, listOf(evidence(DAD))))
        coEvery { places.search("잠실 칼국수", 1, GROUP) } returns ApiResult.Success(
            PlaceSearchPage(
                listOf(
                    PlaceSearchItem("k-9", "칼국수 새집", null, PlaceCategory.RESTAURANT, "서울 송파구", "서울", "송파구", null, null),
                ),
                hasMore = false,
            ),
        )
        val approval = slot<CandidateApproval>()
        coEvery { candidates.approve(candidate, capture(approval)) } returns ApiResult.Success(candidate.copy(status = CandidateStatus.APPROVED))
        val viewModel = started()

        viewModel.edit(candidate)
        assertFalse(viewModel.uiState.value.sheet!!.canSubmit)
        viewModel.onQueryChange("잠실 칼국수")
        viewModel.searchPlace()
        val found = (viewModel.uiState.value.sheet!!.form as CandidateForm.Place).results.single()
        viewModel.onPlaceSelect(found)
        viewModel.onReviewChange("국물이 진함")
        viewModel.submitSheet()

        assertEquals(CandidateApproval.Place("k-9", PlaceCategory.RESTAURANT, "국물이 진함"), approval.captured)
    }

    @Test
    fun 근거를_누르면_가장_앞선_근거_메시지로_가서_추출_부분을_강조한다() = runTest(mainRule.dispatcher) {
        val candidate = schedule(1)
        coEvery { candidates.detail(candidate.id) } returns ApiResult.Success(
            CandidateDetail(candidate, listOf(evidence(MOM, seq = 30, id = ANCHOR, spans = listOf("11시")), evidence(MOM, seq = 28, id = 77, spans = listOf("토요일")))),
        )
        val viewModel = started()

        viewModel.events.test {
            viewModel.showEvidence(candidate)
            assertEquals(CandidateEvent.ShowEvidence(28, mapOf(ANCHOR to listOf("11시"), 77L to listOf("토요일"))), awaitItem())
        }
    }

    @Test
    fun 승인한_항목_보기는_일정_장소_화면으로_보낸다() = runTest(mainRule.dispatcher) {
        val approved = schedule(1).copy(
            status = CandidateStatus.APPROVED,
            entityType = CandidateEntity.SCHEDULE,
            entityId = 55,
        )
        val viewModel = started()

        viewModel.events.test {
            viewModel.openEntity(approved)
            assertEquals(CandidateEvent.OpenEntity(CandidateEntity.SCHEDULE, 55), awaitItem())
        }
    }

    @Test
    fun 기념일은_다가오는_그날로_매년_가족_모두에게_등록한다() {
        val candidate = base(9, CandidateType.ANNIVERSARY, CandidateFields.Anniversary("할머니 생신", 12, 15, "할머니"))
        val today = LocalDate.of(2026, 11, 12)

        val form = candidate.toForm(listOf(me, mom, dad), emptyList(), today) as CandidateForm.Anniversary
        val draft = (form.toApproval(listOf(me, mom, dad), SeoulZone) as CandidateApproval.Schedule).draft

        assertEquals(LocalDate.of(2026, 12, 15), form.date)
        assertTrue(draft.allDay)
        assertEquals("FREQ=YEARLY", draft.rrule)
        assertEquals(listOf(ME, MOM, DAD), draft.participantIds)
        assertEquals("대상: 할머니", draft.memo)
        // 지난 날이면 내년
        val past = base(9, CandidateType.ANNIVERSARY, CandidateFields.Anniversary("결혼기념일", 3, 1, null))
        assertEquals(LocalDate.of(2027, 3, 1), (past.toForm(emptyList(), emptyList(), today) as CandidateForm.Anniversary).date)
    }

    @Test
    fun 참석자를_못_찾았으면_말한_사람으로_채운다() {
        val candidate = schedule(1).let {
            it.copy(fields = (it.fields as CandidateFields.Schedule).copy(allMembers = false, location = null))
        }

        val form = candidate.toForm(listOf(me, mom, dad), listOf(evidence(MOM)), LocalDate.of(2026, 11, 12)) as CandidateForm.Schedule

        assertEquals(setOf(MOM), form.participantIds)
        assertNotNull(form.toApproval(listOf(me, mom, dad), SeoulZone))
    }

    // ---- 도우미 ----

    private fun message(seq: Long) = ChatMessage(
        key = "k$seq", groupId = GROUP, id = if (seq == 30L) ANCHOR else seq, seq = seq, senderId = MOM, clientMessageId = null,
        type = MessageType.TEXT, body = "b$seq", replyTo = null, reactions = emptyList(), unreadCount = 0,
        deleted = false, sentAt = clock.instant(), sendState = SendState.SENT,
    )

    private fun base(id: Long, type: CandidateType, fields: CandidateFields) = Candidate(
        id = id, groupId = GROUP, type = type, status = CandidateStatus.PENDING, fields = fields, inferred = emptySet(),
        missingFields = emptySet(), confidence = 0.9, surface = CandidateSurface.CARD, anchorMessageId = ANCHOR, anchorSeq = 30,
        version = 3, expiresAt = Instant.parse("2026-11-14T02:00:00Z"), createdAt = clock.instant().plusSeconds(id),
    )

    private fun schedule(id: Long) = base(
        id,
        CandidateType.SCHEDULE,
        CandidateFields.Schedule(
            title = "할머니 댁 방문", date = LocalDate.of(2026, 11, 14), startTime = LocalTime.of(11, 0), endTime = null,
            location = CandidateLocation("할머니 댁", null, null), attendeeIds = emptyList(), allMembers = true,
            attendeeChoices = emptyList(), unmatchedAttendees = emptyList(),
        ),
    ).copy(inferred = setOf(Candidate.INFERRED_AMPM, Candidate.INFERRED_ATTENDEES))

    private fun todo(choices: List<List<Long>>) = base(
        6,
        CandidateType.TODO,
        CandidateFields.Todo("자동차 점검 맡기기", LocalDate.of(2026, 11, 15), emptyList(), false, choices, emptyList()),
    )

    private fun place() = base(
        7,
        CandidateType.PLACE,
        CandidateFields.Place(
            name = "OO칼국수", regionHint = "잠실", kakaoPlaceId = null,
            options = listOf(
                PlaceOption("k-1", "OO칼국수 잠실점", PlaceCategory.RESTAURANT, null, null, "서울", "송파구"),
                PlaceOption("k-2", "OO칼국수 본점", PlaceCategory.RESTAURANT, null, null, "서울", "송파구"),
            ),
            opinion = "칼국수가 정말 맛있음", recommendedBy = DAD,
        ),
    )

    private fun evidence(sender: Long, seq: Long = 30, id: Long = ANCHOR, spans: List<String> = emptyList()) =
        CandidateEvidence(id, seq, sender, clock.instant(), spans, "이번 토요일 11시에 할머니 댁 가자")

    /** 되돌리기 5분을 넘기려고 시각을 옮기는 시계 */
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private companion object {
        const val GROUP = 1L
        const val ME = 1L
        const val MOM = 2L
        const val DAD = 3L
        const val ANCHOR = 10231L
    }
}
