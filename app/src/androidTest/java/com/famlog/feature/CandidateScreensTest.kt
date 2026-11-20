package com.famlog.feature

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateFields
import com.famlog.core.model.CandidateLocation
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.CandidateSurface
import com.famlog.core.model.CandidateType
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.DismissReason
import com.famlog.core.model.GroupRole
import com.famlog.core.model.ItemSource
import com.famlog.core.model.Member
import com.famlog.core.model.MessageType
import com.famlog.core.model.Schedule
import com.famlog.core.model.SendState
import com.famlog.feature.calendar.ScheduleDetailContent
import com.famlog.feature.calendar.ScheduleDetailUiState
import com.famlog.feature.chat.CandidateCardActions
import com.famlog.feature.chat.CandidateForm
import com.famlog.feature.chat.CandidateInboxContent
import com.famlog.feature.chat.CandidateInboxUiState
import com.famlog.feature.chat.CandidateSheet
import com.famlog.feature.chat.CandidateSheetActions
import com.famlog.feature.chat.CandidateSheetContent
import com.famlog.feature.chat.CandidateUiState
import com.famlog.feature.chat.ChatRoomContent
import com.famlog.feature.chat.ChatRoomUiState
import com.famlog.feature.chat.SeoulZone
import com.famlog.feature.chat.buildChatRows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** 채팅방 후보 카드·Resolved Chip·수정 시트 (ARC-05, SCR-13) */
@RunWith(AndroidJUnit4::class)
class CandidateScreensTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private val me = Member(7, "윤서", GroupRole.MEMBER, null, "딸", null)
    private val mom = Member(8, "김엄마", GroupRole.MEMBER, null, "엄마", null)
    private val members = listOf(me, mom)
    private val anchor = ChatMessage(
        key = "k30", groupId = 1, id = 30, seq = 30, senderId = 8, clientMessageId = null, type = MessageType.TEXT,
        body = "이번 토요일 11시에 할머니 댁 가자", replyTo = null, reactions = emptyList(), unreadCount = 0, deleted = false,
        sentAt = OffsetDateTime.parse("2026-11-12T14:10:00+09:00").toInstant(), sendState = SendState.SENT,
    )
    private val candidate = Candidate(
        id = 1, groupId = 1, type = CandidateType.SCHEDULE, status = CandidateStatus.PENDING,
        fields = CandidateFields.Schedule(
            "할머니 댁 방문", LocalDate.of(2026, 11, 14), LocalTime.of(11, 0), null,
            CandidateLocation("할머니 댁", null, null), emptyList(), true, emptyList(), emptyList(),
        ),
        inferred = setOf(Candidate.INFERRED_AMPM, Candidate.INFERRED_ATTENDEES), missingFields = emptySet(), confidence = 0.9,
        surface = CandidateSurface.CARD, anchorMessageId = 30, anchorSeq = 30, version = 1,
        createdAt = OffsetDateTime.parse("2026-11-12T14:10:30+09:00").toInstant(),
    )

    private fun room(candidates: CandidateUiState, actions: CandidateCardActions = CandidateCardActions()) {
        compose.setContent {
            FamLogTheme {
                ChatRoomContent(
                    state = ChatRoomUiState(
                        groupName = "우리 가족", memberCount = 2, members = members.associateBy { it.userId }, myUserId = 7,
                        rows = buildChatRows(listOf(anchor), SeoulZone), today = today,
                    ),
                    onBack = {}, onInputChange = {}, onSend = {}, onCancelReply = {}, onLoadOlder = {},
                    onLongPress = {}, onReactionClick = { _, _ -> }, onInvite = {},
                    candidates = candidates, candidateActions = actions,
                )
            }
        }
    }

    @Test
    fun 근거_메시지_아래_카드에_요약_추정_근거_AI_제안과_버튼이_보인다() {
        var approved: Candidate? = null
        var dismissed: DismissReason? = null
        room(
            CandidateUiState(byAnchor = mapOf(30L to listOf(candidate)), members = members, today = today),
            CandidateCardActions(onApprove = { approved = it }, onDismiss = { _, reason -> dismissed = reason }),
        )

        compose.onNodeWithText("일정을 찾았어요").assertExists()
        compose.onNodeWithText("할머니 댁 방문").assertExists()
        compose.onNodeWithText("11/14(토) 오전 11:00 · 할머니 댁").assertExists()
        compose.onNodeWithText("참석: 가족 전체").assertExists()
        compose.onNodeWithText("추정").assertExists()
        compose.onNodeWithText("오전·오후, 참석자").assertExists()
        compose.onNodeWithText("엄마 · 오후 2:10 · 원문 보기").assertExists()
        compose.onNodeWithText("AI 제안").assertExists()

        compose.onNodeWithText("등록").performClick()
        assertEquals(1L, approved?.id)

        compose.onNodeWithText("무시").performClick()
        compose.onNodeWithText("농담이었어요").performClick()
        assertEquals(DismissReason.JOKE, dismissed)
    }

    @Test
    fun 승인한_후보는_칩으로_접히고_되돌리기는_허용될_때만_보인다() {
        val approved = candidate.copy(
            status = CandidateStatus.APPROVED, decidedBy = 8, entityType = CandidateEntity.SCHEDULE, entityId = 55,
        )
        var opened: Candidate? = null
        room(
            CandidateUiState(byAnchor = mapOf(30L to listOf(approved)), members = members, undoable = emptySet()),
            CandidateCardActions(onOpen = { opened = it }),
        )

        compose.onNodeWithText("캘린더에 등록됨 · 엄마 ·").assertExists()
        compose.onNodeWithText("일정을 찾았어요").assertDoesNotExist()
        compose.onNodeWithText("되돌리기").assertDoesNotExist()
        compose.onNode(hasText("보기") and hasClickAction()).performClick()
        assertEquals(55L, opened?.entityId)
    }

    @Test
    fun 칩의_되돌리기를_누르면_되돌린다() {
        val approved = candidate.copy(status = CandidateStatus.APPROVED, decidedBy = 7, entityType = CandidateEntity.SCHEDULE, entityId = 55)
        var undone: Candidate? = null
        room(
            CandidateUiState(byAnchor = mapOf(30L to listOf(approved)), members = members, undoable = setOf(1L)),
            CandidateCardActions(onUndo = { undone = it }),
        )

        compose.onNodeWithText("되돌리기").performClick()
        assertEquals(1L, undone?.id)
    }

    @Test
    fun 수정_시트는_AI_추정_칸을_표시하고_빈_필수_칸이_있으면_등록을_막는다() {
        val form = CandidateForm.Schedule(
            title = "", date = LocalDate.of(2026, 11, 14), allDay = false, startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(11, 0), participantIds = setOf(7, 8), locationText = "할머니 댁", placeId = null,
            originalLocation = "할머니 댁",
        )
        compose.setContent {
            FamLogTheme {
                CandidateSheetContent(
                    sheet = CandidateSheet(candidate, form = form),
                    members = members,
                    today = today,
                    actions = CandidateSheetActions(),
                )
            }
        }

        compose.onNodeWithText("일정 등록").assertExists()
        compose.onNodeWithText("입력해 주세요").assertExists()
        compose.onNodeWithText("등록").assertIsNotEnabled()
        // 시간(오전·오후)과 참석자는 AI 추정
        assertEquals(2, compose.onAllNodes(hasText("AI 추정 · 확인 필요")).fetchSemanticsNodes().size)
    }

    @Test
    fun 제안함은_안내와_대기_카드를_보이고_근거를_안_받았으면_원문_보기만() {
        val low = candidate.copy(id = 2, surface = CandidateSurface.INBOX, anchorMessageId = 12, anchorSeq = 12)
        var original: Long? = null
        compose.setContent {
            FamLogTheme {
                CandidateInboxContent(
                    state = CandidateInboxUiState(
                        items = listOf(low, candidate), anchors = mapOf(30L to anchor), members = members, loading = false,
                    ),
                    onBack = {}, onRetry = {}, onLoadMore = {},
                    cardActions = CandidateCardActions(onShowEvidence = { original = it.id }),
                )
            }
        }

        compose.onNodeWithText("제안함").assertExists()
        compose.onNodeWithText("AI가 대화에서 찾은 일정·할 일·장소 제안이에요. 등록하기 전에는 저장되지 않아요.").assertExists()
        // 근거를 받아 둔 카드만 인용이 있다
        compose.onNodeWithText("“이번 토요일 11시에 할머니 댁 가자”").assertExists()
        compose.onAllNodes(hasText("원문 보기")).onFirst().performClick()
        assertEquals(2L, original)
    }

    @Test
    fun 제안함이_비었으면_안내한다() {
        compose.setContent {
            FamLogTheme {
                CandidateInboxContent(
                    state = CandidateInboxUiState(loading = false),
                    onBack = {}, onRetry = {}, onLoadMore = {}, cardActions = CandidateCardActions(),
                )
            }
        }

        compose.onNodeWithText("대기 중인 AI 제안이 없어요.").assertExists()
    }

    @Test
    fun 채팅방_앱바_제안함_배지는_더_있으면_N_플러스() {
        var opened = false
        compose.setContent {
            FamLogTheme {
                ChatRoomContent(
                    state = ChatRoomUiState(groupName = "우리 가족", memberCount = 2, today = today),
                    onBack = {}, onInputChange = {}, onSend = {}, onCancelReply = {}, onLoadOlder = {},
                    onLongPress = {}, onReactionClick = { _, _ -> }, onInvite = {},
                    candidates = CandidateUiState(pending = 50, pendingMore = true),
                    onOpenInbox = { opened = true },
                )
            }
        }

        compose.onNodeWithText("50+").assertExists()
        compose.onNodeWithContentDescription("제안함 열기, 대기 중인 AI 제안 50+개").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun 채팅에서_등록한_일정_상세는_원문_보기로_간다() {
        val schedule = Schedule(
            id = 6, groupId = 1, title = "할머니 댁 방문", allDay = false,
            startsAt = OffsetDateTime.parse("2026-11-21T11:00:00+09:00").toInstant(),
            endsAt = OffsetDateTime.parse("2026-11-21T11:00:00+09:00").toInstant(),
            startDate = LocalDate.of(2026, 11, 21), endDate = LocalDate.of(2026, 11, 21), participantIds = listOf(7, 8),
            rrule = null, memo = null, reminders = emptyList(), createdBy = 7, version = 0, source = ItemSource(35, 35),
        )
        var opened: Pair<Long, ItemSource>? = null
        compose.setContent {
            FamLogTheme {
                ScheduleDetailContent(
                    state = ScheduleDetailUiState(loading = false, schedule = schedule, members = members, myUserId = 7),
                    onBack = {}, onEdit = {}, onDelete = {},
                    onOpenSource = { groupId, source -> opened = groupId to source },
                )
            }
        }

        compose.onNodeWithText("채팅에서 등록").assertExists()
        compose.onNodeWithText("원문 보기").performClick()
        assertEquals(1L to ItemSource(35, 35), opened)
    }

    @Test
    fun 수정_시트는_필수_칸이_다_차면_등록할_수_있다() {
        var submitted = false
        val form = CandidateForm.Todo(title = "자동차 점검 맡기기", assigneeId = null, dueDate = null)
        compose.setContent {
            FamLogTheme {
                CandidateSheetContent(
                    sheet = CandidateSheet(
                        candidate.copy(type = CandidateType.TODO),
                        form = form,
                        serverMissing = setOf(CandidateForm.FIELD_ASSIGNEES),
                    ),
                    members = members,
                    today = today,
                    actions = CandidateSheetActions(onSubmit = { submitted = true }),
                )
            }
        }

        compose.onNodeWithText("할 일 등록").assertExists()
        // 서버가 골라 달라고 한 담당자 칸을 강조한다
        compose.onNodeWithText("AI 추정 · 확인 필요").assertExists()
        compose.onNodeWithText("등록").assertIsEnabled().performClick()
        assertEquals(true, submitted)
    }
}
