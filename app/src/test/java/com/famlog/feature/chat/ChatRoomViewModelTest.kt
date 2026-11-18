package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.famlog.core.data.ChatPresence
import com.famlog.core.data.ChatRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.Member
import com.famlog.core.model.MessageType
import com.famlog.core.model.LocalMedia
import com.famlog.core.model.MediaKind
import com.famlog.core.media.MediaInspector
import com.famlog.core.media.PickedMedia
import com.famlog.core.designsystem.UiText
import com.famlog.R
import app.cash.turbine.test
import com.famlog.core.model.SendState
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ChatRoomViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val chat = mockk<ChatRepository>(relaxed = true) {
        every { messages(GROUP) } returns messages
        coEvery { refresh(GROUP) } returns ApiResult.Success(Unit)
    }
    private val me = Member(ME, "윤서", GroupRole.OWNER, null, "막내", null)
    private val mom = Member(OTHER, "김엄마", GroupRole.MEMBER, null, "엄마", null)
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { groups } returns MutableStateFlow(listOf(GroupSummary(GROUP, "우리 가족", 2, GroupRole.OWNER)))
        every { details } returns MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.OWNER, listOf(me, mom))))
    }
    private val clock = Clock.fixed(Instant.parse("2026-11-17T05:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val inspector = mockk<MediaInspector>()
    private val presence = mockk<ChatPresence>(relaxed = true)

    private fun viewModel() = ChatRoomViewModel(
        SavedStateHandle(mapOf("groupId" to GROUP)), chat, groups, FakeSessionStore(userId = ME), clock,
        inspector, "http://server/api/v1/", presence,
    )

    private fun message(seq: Long?, sender: Long, state: SendState = SendState.SENT) = ChatMessage(
        key = "k$seq", groupId = GROUP, id = seq, seq = seq, senderId = sender, clientMessageId = "c$seq",
        type = MessageType.TEXT, body = "b$seq", replyTo = null, reactions = emptyList(), unreadCount = 0,
        deleted = false, sentAt = clock.instant(), sendState = state,
    )

    @Test
    fun 제목은_그룹_이름과_인원_보낸_사람은_호칭으로_보인다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals("우리 가족", state.groupName)
        assertEquals(2, state.memberCount)
        assertEquals("엄마", state.nameOf(OTHER))
    }

    @Test
    fun 보내면_입력과_답장을_비우고_답장_대상과_함께_보낸다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        val target = message(3, OTHER)

        viewModel.startReply(target)
        viewModel.onInputChange("  가는 길에 과일 사 갈게요  ")
        viewModel.send()

        coVerify { chat.send(GROUP, "가는 길에 과일 사 갈게요", target) }
        assertEquals("", viewModel.uiState.value.input)
        assertNull(viewModel.uiState.value.replyTo)
    }

    @Test
    fun 빈_입력은_보내지_않는다() = runTest {
        val viewModel = viewModel()
        viewModel.onInputChange("   ")
        viewModel.send()

        coVerify(exactly = 0) { chat.send(any(), any(), any()) }
    }

    @Test
    fun 화면이_보이는_동안_새_메시지가_오면_읽음을_알린다() = runTest {
        val viewModel = viewModel()
        messages.value = listOf(message(1, OTHER))
        coVerify(exactly = 0) { chat.markRead(GROUP) }

        viewModel.setVisible(true)
        coVerify(exactly = 1) { chat.markRead(GROUP) }

        messages.value = listOf(message(1, OTHER), message(2, OTHER))
        coVerify(exactly = 2) { chat.markRead(GROUP) }

        viewModel.setVisible(false)
        messages.value = messages.value + message(3, OTHER)
        coVerify(exactly = 2) { chat.markRead(GROUP) }
    }

    @Test
    fun 메뉴_동작은_메시지_상태에_따라_달라진다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        every { chat.canDelete(any(), ME) } answers { firstArg<ChatMessage>().senderId == ME }

        val others = viewModel.actionsFor(message(1, OTHER))
        assertTrue(others.canReact && others.canReply && others.canReport)
        assertFalse(others.canDelete || others.canRetry)

        val mine = viewModel.actionsFor(message(2, ME))
        assertTrue(mine.canDelete)
        assertFalse(mine.canReport)

        val failed = viewModel.actionsFor(message(null, ME, SendState.FAILED))
        assertTrue(failed.canRetry && failed.canDiscard)
        assertFalse(failed.canReact || failed.canReply)
    }

    @Test
    fun 고른_파일_중_보낼_수_없는_것은_빼고_알린_뒤_나머지를_보낸다() = runTest {
        val photo = LocalMedia("content://1", "image/jpeg", 10, MediaKind.IMAGE)
        coEvery { inspector.inspect(listOf("content://1", "content://2")) } returns PickedMedia(listOf(photo), skipped = 1)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onMediaPicked(listOf("content://1", "content://2"))
            assertEquals(ChatRoomEvent.Message(UiText.Res(R.string.chat_media_skipped, listOf(1))), awaitItem())
        }
        coVerify { chat.sendMedia(GROUP, listOf(photo)) }
    }

    private companion object {
        const val GROUP = 1L
        const val ME = 7L
        const val OTHER = 8L
    }
}
