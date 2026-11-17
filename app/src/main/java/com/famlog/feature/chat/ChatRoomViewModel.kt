package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.ChatRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.media.MediaInspector
import com.famlog.core.network.ApiBaseUrl
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.ReactionEmoji
import com.famlog.core.model.SendState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ChatRoomUiState(
    val groupName: String = "",
    val memberCount: Int = 0,
    val members: Map<Long, Member> = emptyMap(),
    val myUserId: Long? = null,
    val myRole: GroupRole = GroupRole.MEMBER,
    val rows: List<ChatRow> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
    val input: String = "",
    val replyTo: ChatMessage? = null,
    val loadingOlder: Boolean = false,
    val hasOlder: Boolean = true,
    val inviting: Boolean = false,
    /** 사진 주소를 만들 서버 주소 (…/api/v1/) */
    val mediaBaseUrl: String = "",
) {
    val canSend: Boolean get() = input.isNotBlank()

    fun nameOf(userId: Long?): String? = userId?.let { members[it]?.displayName }
}

sealed interface ChatRoomEvent {
    data class Message(val text: UiText) : ChatRoomEvent
    data class ShareInvite(val groupName: String, val code: String) : ChatRoomEvent
}

/** 메시지 메뉴(길게 누르기)에 보일 동작 */
data class MessageActions(
    val canReact: Boolean,
    val canReply: Boolean,
    val canDelete: Boolean,
    val canReport: Boolean,
    val canRetry: Boolean,
    val canDiscard: Boolean,
)

/** SCR-11 그룹 채팅방 + SCR-12 서랍 (CHAT-01~04·06) */
@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
    private val mediaInspector: MediaInspector,
    @ApiBaseUrl private val apiBaseUrl: String,
) : ViewModel() {

    val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val local = MutableStateFlow(LocalState())
    private val myUserId = MutableStateFlow<Long?>(null)
    private val visible = MutableStateFlow(false)
    private val messages: Flow<List<ChatMessage>> = chatRepository.messages(groupId)

    private data class LocalState(
        val input: String = "",
        val replyTo: ChatMessage? = null,
        val loadingOlder: Boolean = false,
        val hasOlder: Boolean = true,
        val inviting: Boolean = false,
    )

    val uiState: StateFlow<ChatRoomUiState> = combine(
        messages,
        groupRepository.groups,
        groupRepository.details,
        myUserId,
        local,
    ) { messages, groups, details, me, local ->
        val summary = groups.firstOrNull { it.id == groupId }
        val detail = details[groupId]
        ChatRoomUiState(
            groupName = summary?.name ?: detail?.name.orEmpty(),
            memberCount = detail?.members?.size ?: summary?.memberCount ?: 0,
            members = detail?.members?.associateBy { it.userId }.orEmpty(),
            myUserId = me,
            myRole = summary?.myRole ?: detail?.myRole ?: GroupRole.MEMBER,
            rows = buildChatRows(messages, SeoulZone),
            today = LocalDate.now(clock),
            input = local.input,
            replyTo = local.replyTo,
            loadingOlder = local.loadingOlder,
            hasOlder = local.hasOlder,
            inviting = local.inviting,
            mediaBaseUrl = apiBaseUrl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatRoomUiState())

    private val _events = Channel<ChatRoomEvent>(Channel.BUFFERED)
    val events: Flow<ChatRoomEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { myUserId.value = sessionStore.userId() }
        viewModelScope.launch { groupRepository.detail(groupId) }
        viewModelScope.launch {
            val result = chatRepository.refresh(groupId)
            if (result !is ApiResult.Success && messages.first().isEmpty()) {
                _events.send(ChatRoomEvent.Message(UiText.Res(R.string.chat_load_failed)))
            }
        }
        // 화면이 보이는 동안 새 메시지가 오면 읽음으로 알린다 (CHAT-04)
        viewModelScope.launch {
            combine(visible, messages.map { list -> list.lastOrNull { it.seq != null }?.seq }) { shown, lastSeq ->
                shown to lastSeq
            }
                .distinctUntilChanged()
                .filter { (shown, lastSeq) -> shown && lastSeq != null }
                .collect { chatRepository.markRead(groupId) }
        }
    }

    fun setVisible(value: Boolean) {
        visible.value = value
    }

    fun onInputChange(value: String) {
        local.update { it.copy(input = value.take(MAX_BODY_LENGTH)) }
    }

    fun send() {
        val state = local.value
        val body = state.input.trim()
        if (body.isEmpty()) return
        local.update { it.copy(input = "", replyTo = null) }
        viewModelScope.launch { chatRepository.send(groupId, body, state.replyTo) }
    }

    /** Photo Picker로 고른 파일 (CHAT-05). 보낼 수 없는 것은 빼고 알린다 */
    fun onMediaPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val picked = mediaInspector.inspect(uris)
            if (picked.skipped > 0) {
                _events.send(ChatRoomEvent.Message(UiText.Res(R.string.chat_media_skipped, listOf(picked.skipped))))
            }
            if (picked.items.isNotEmpty()) chatRepository.sendMedia(groupId, picked.items)
        }
    }

    fun startReply(message: ChatMessage) {
        if (message.id == null) return
        local.update { it.copy(replyTo = message) }
    }

    fun cancelReply() {
        local.update { it.copy(replyTo = null) }
    }

    fun loadOlder() {
        val state = local.value
        if (state.loadingOlder || !state.hasOlder) return
        local.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val result = chatRepository.loadOlder(groupId)
            local.update {
                it.copy(
                    loadingOlder = false,
                    hasOlder = if (result is ApiResult.Success) result.value else it.hasOlder,
                )
            }
        }
    }

    fun actionsFor(message: ChatMessage): MessageActions {
        val me = uiState.value.myUserId
        val sent = message.sendState == SendState.SENT && message.id != null
        val usable = sent && !message.deleted && !message.isSystem
        return MessageActions(
            canReact = usable,
            canReply = usable,
            canDelete = chatRepository.canDelete(message, me),
            canReport = usable && message.senderId != me,
            canRetry = message.sendState == SendState.FAILED,
            canDiscard = message.sendState != SendState.SENT,
        )
    }

    fun toggleReaction(message: ChatMessage, emoji: ReactionEmoji) = launchWithError {
        chatRepository.toggleReaction(message, emoji)
    }

    fun delete(message: ChatMessage) = launchWithError { chatRepository.delete(message) }

    fun retry(message: ChatMessage) {
        viewModelScope.launch { chatRepository.retry(message.key) }
    }

    fun discard(message: ChatMessage) {
        viewModelScope.launch { chatRepository.discard(message.key) }
    }

    /** 신고 API는 S20 (MOD-01). 지금은 자리만 */
    fun report() {
        viewModelScope.launch { _events.send(ChatRoomEvent.Message(UiText.Res(R.string.chat_report_coming))) }
    }

    /** 서랍의 [가족 초대하기] (그룹장·관리자, GRP-02) */
    fun invite() {
        if (local.value.inviting) return
        local.update { it.copy(inviting = true) }
        viewModelScope.launch {
            when (val result = groupRepository.issueInvite(groupId)) {
                is ApiResult.Success -> _events.send(ChatRoomEvent.ShareInvite(uiState.value.groupName, result.value.code))
                else -> _events.send(ChatRoomEvent.Message(result.toUiText()))
            }
            local.update { it.copy(inviting = false) }
        }
    }

    private fun launchWithError(block: suspend () -> ApiResult<*>) {
        viewModelScope.launch {
            val result = block()
            if (result !is ApiResult.Success) _events.send(ChatRoomEvent.Message(result.toUiText()))
        }
    }

    private companion object {
        /** 서버 ChatDtos.MAX_BODY_LENGTH */
        const val MAX_BODY_LENGTH = 2000
    }
}
