package com.famlog.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.common.IoDispatcher
import com.famlog.core.data.ChatRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.model.ChatMedia
import com.famlog.core.network.ApiBaseUrl
import com.famlog.core.network.MediaUrls
import com.famlog.core.network.MediaVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 사진·동영상 전체 화면 보기. 메시지의 미디어가 처리 완료(media.updated)되면 바로 바뀐다 */
@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    chatRepository: ChatRepository,
    sessionStore: SessionStore,
    private val mediaUrls: MediaUrls,
    @ApiBaseUrl val baseUrl: String,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])
    private val messageId: Long = checkNotNull(savedStateHandle["messageId"])
    val initialIndex: Int = savedStateHandle["index"] ?: 0

    private val message = chatRepository.messages(groupId).map { list -> list.firstOrNull { it.id == messageId } }

    val media: StateFlow<List<ChatMedia>?> = message
        .map { it?.media.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 다른 가족이 보낸 사진·동영상만 [신고] (MOD-01) */
    val reportable: StateFlow<Boolean> = combine(message, flow { emit(sessionStore.userId()) }) { message, me ->
        message != null && !message.deleted && me != null && message.senderId != me
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun imageUrl(mediaId: Long): String = MediaUrls.contentUrl(baseUrl, mediaId, MediaVariant.THUMB_L)

    /** 동영상 원본의 저장소 주소 (10분). VideoView는 인증 헤더를 넣지 못해 미리 받는다 */
    suspend fun videoUrl(mediaId: Long): String? = withContext(io) { mediaUrls.resolve(mediaId, MediaVariant.ORIGINAL) }
}
