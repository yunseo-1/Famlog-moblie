package com.famlog.feature

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.ChatMedia
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.LocalMedia
import com.famlog.core.model.MediaKind
import com.famlog.core.model.MediaStatus
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.LastMessage
import com.famlog.core.model.Member
import com.famlog.core.model.MessageType
import com.famlog.core.model.SendState
import com.famlog.feature.chat.ChatListContent
import com.famlog.feature.chat.ChatListUiState
import com.famlog.feature.chat.ChatRoomContent
import com.famlog.feature.chat.ChatRoomUiState
import com.famlog.feature.chat.SeoulZone
import com.famlog.feature.chat.buildChatRows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
class ChatScreensTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 17)
    private val mom = Member(8, "김엄마", GroupRole.MEMBER, null, "엄마", null)
    private val me = Member(7, "윤서", GroupRole.OWNER, null, null, null)

    private fun message(seq: Long, sender: Long?, body: String, type: MessageType = MessageType.TEXT, unread: Int = 0) =
        ChatMessage(
            key = "k$seq", groupId = 1, id = seq, seq = seq, senderId = sender, clientMessageId = "c$seq",
            type = type, body = body, replyTo = null, reactions = emptyList(), unreadCount = unread, deleted = false,
            sentAt = OffsetDateTime.parse("2026-11-17T15:12:00+09:00").toInstant(), sendState = SendState.SENT,
        )

    @Test
    fun 채팅_탭은_보낸_사람_호칭과_마지막_메시지_안_읽은_수를_보인다() {
        val group = GroupSummary(
            1, "우리 가족", 4, GroupRole.OWNER,
            LastMessage(5, 8, MessageType.TEXT, "누가 휴지 좀 사와", false, OffsetDateTime.parse("2026-11-17T15:12:00+09:00").toInstant()),
            unreadCount = 3,
        )
        var opened: Long? = null
        compose.setContent {
            FamLogTheme {
                ChatListContent(
                    state = ChatListUiState(
                        groups = listOf(group),
                        details = mapOf(1L to GroupDetail(1, "우리 가족", GroupRole.OWNER, listOf(me, mom))),
                        myUserId = 7,
                        today = today,
                    ),
                    onOpenRoom = { opened = it },
                    onNewGroup = {},
                )
            }
        }

        compose.onNodeWithText("우리 가족 · 4명").assertExists()
        compose.onNodeWithText("엄마: 누가 휴지 좀 사와").assertExists()
        compose.onNodeWithText("오후 3:12").assertExists()
        compose.onNodeWithContentDescription("안 읽은 메시지 3개").assertExists()
        compose.onNodeWithText("우리 가족 · 4명").performClick()
        assertEquals(1L, opened)
    }

    @Test
    fun 채팅방은_말풍선_시스템_메시지_안_읽은_인원을_보이고_길게_누르면_메뉴를_연다() {
        val messages = listOf(
            message(1, null, "엄마님이 들어왔어요.", MessageType.SYSTEM),
            message(2, 8, "이번 토요일 11시에 할머니 댁 가자"),
            message(3, 7, "좋아요! 가는 길에 과일 사 갈게요", unread = 1),
        )
        var longPressed: ChatMessage? = null
        compose.setContent {
            FamLogTheme {
                ChatRoomContent(
                    state = ChatRoomUiState(
                        groupName = "우리 가족",
                        memberCount = 2,
                        members = mapOf(7L to me, 8L to mom),
                        myUserId = 7,
                        rows = buildChatRows(messages, SeoulZone),
                        today = today,
                    ),
                    onBack = {}, onInputChange = {}, onSend = {}, onCancelReply = {}, onLoadOlder = {},
                    onLongPress = { longPressed = it }, onReactionClick = { _, _ -> }, onInvite = {},
                )
            }
        }

        compose.onNodeWithText("우리 가족 (2)").assertExists()
        compose.onNodeWithText("11월 17일 화요일").assertExists()
        compose.onNodeWithText("엄마님이 들어왔어요.").assertExists()
        // 상대 묶음마다 이름(엄마) — 서랍의 구성원 목록에도 있다
        assert(compose.onAllNodesWithText("엄마").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithContentDescription("안 읽은 사람 1명").assertExists()
        compose.onNodeWithContentDescription("보내기").assertIsNotEnabled()

        compose.onNodeWithText("이번 토요일 11시에 할머니 댁 가자").performTouchInput { longClick() }
        assertEquals(2L, longPressed?.seq)
    }

    @Test
    fun 사진_묶음은_올리는_중_진행과_처리_중_칸_9장_넘으면_더보기를_보인다() {
        val uploading = message(5, 7, "", MessageType.IMAGE).copy(
            key = "up", id = null, seq = null, sendState = SendState.SENDING,
            localMedia = listOf(
                LocalMedia("content://1", "image/jpeg", 1, MediaKind.IMAGE, mediaId = 1),
                LocalMedia("content://2", "image/jpeg", 1, MediaKind.IMAGE),
            ),
        )
        val many = message(6, 8, "", MessageType.IMAGE).copy(
            media = (1L..11L).map { ChatMedia(it, MediaKind.IMAGE, MediaStatus.UPLOADED, null, null, null) },
        )
        val video = message(7, 8, "", MessageType.VIDEO).copy(
            media = listOf(ChatMedia(20, MediaKind.VIDEO, MediaStatus.UPLOADED, null, null, 65)),
        )
        compose.setContent {
            FamLogTheme {
                ChatRoomContent(
                    state = ChatRoomUiState(
                        groupName = "우리 가족", memberCount = 2, members = mapOf(7L to me, 8L to mom), myUserId = 7,
                        rows = buildChatRows(listOf(many, video, uploading), SeoulZone), today = today,
                        mediaBaseUrl = "http://server/api/v1/",
                    ),
                    onBack = {}, onInputChange = {}, onSend = {}, onCancelReply = {}, onLoadOlder = {},
                    onLongPress = {}, onReactionClick = { _, _ -> }, onInvite = {}, onAttach = {},
                )
            }
        }

        compose.onNodeWithText("올리는 중 1/2").assertExists()
        compose.onNodeWithText("+2").assertExists()
        compose.onNodeWithContentDescription("동영상 1:05").assertExists()
        compose.onNodeWithText("처리 중").assertExists()
        compose.onNodeWithContentDescription("사진·동영상 보내기").assertExists()
    }

    @Test
    fun 입력이_있으면_보내기를_누를_수_있다() {
        var sent = false
        compose.setContent {
            FamLogTheme {
                ChatRoomContent(
                    state = ChatRoomUiState(groupName = "우리 가족", memberCount = 1, input = "안녕", today = today),
                    onBack = {}, onInputChange = {}, onSend = { sent = true }, onCancelReply = {}, onLoadOlder = {},
                    onLongPress = {}, onReactionClick = { _, _ -> }, onInvite = {},
                )
            }
        }

        compose.onNodeWithContentDescription("보내기").assertIsEnabled().performClick()
        assertEquals(true, sent)
    }
}
