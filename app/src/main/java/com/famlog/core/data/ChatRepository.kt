package com.famlog.core.data

import com.famlog.core.database.MessageDao
import com.famlog.core.database.MessageEntity
import com.famlog.core.datastore.SessionStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.LastMessage
import com.famlog.core.model.LocalMedia
import com.famlog.core.model.MediaKind
import com.famlog.core.model.MessageType
import com.famlog.core.model.ReactionEmoji
import com.famlog.core.model.SendState
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.ChatApi
import com.famlog.core.network.ChatErrorEvent
import com.famlog.core.network.ChatEventEnvelope
import com.famlog.core.network.ChatEventTypes
import com.famlog.core.network.ChatSocket
import com.famlog.core.network.MediaResponse
import com.famlog.core.network.MediaUpdated
import com.famlog.core.network.MessageDeleted
import com.famlog.core.network.MessageResponse
import com.famlog.core.network.ReactionRequest
import com.famlog.core.network.ReactionResponse
import com.famlog.core.network.ReactionsResponse
import com.famlog.core.network.ReadRequest
import com.famlog.core.network.ReadUpdated
import com.famlog.core.network.ReplyPreviewResponse
import com.famlog.core.network.SendMessageRequest
import com.famlog.core.network.parseServerTime
import com.famlog.core.network.toModel
import com.famlog.core.network.toReactions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅 메시지 (CHAT-02~06). 화면은 Room 캐시만 보고, 서버·실시간 이벤트는 캐시를 고친다.
 *
 * - 보내기: 캐시에 SENDING으로 먼저 넣고 STOMP로 보낸다. 서버가 같은 clientMessageId로 돌려주면 그 줄이 SENT로 바뀐다.
 *   연결이 없으면 SENDING으로 남아 있다가 연결되면 [flushPending]이 같은 clientMessageId로 다시 보낸다 (서버 멱등).
 * - 순서: 화면은 언제나 seq 순서. 받은 seq가 캐시의 마지막 seq + 1보다 크면 빈 곳을 afterSeq로 채운다.
 * - 메시지 본문은 로그에 남기지 않는다 (CLAUDE.md 3장).
 */
@Singleton
class ChatRepository @Inject constructor(
    private val api: ChatApi,
    private val caller: ApiCaller,
    private val dao: MessageDao,
    private val socket: ChatSocket,
    private val sessionStore: SessionStore,
    private val groupRepository: GroupRepository,
    private val json: Json,
    private val clock: Clock,
    private val uploadScheduler: UploadScheduler,
) {
    /** 그룹별로 마지막으로 보낸 읽음 seq. 같은 값을 여러 번 보내지 않는다 */
    private val sentReadSeq = mutableMapOf<Long, Long>()
    private val readLock = Mutex()

    fun messages(groupId: Long): Flow<List<ChatMessage>> =
        dao.observe(groupId).map { list -> list.map { it.toModel() } }

    /**
     * 채팅방에 들어올 때. 캐시가 없으면 최신 한 페이지를, 있으면 마지막 seq 이후만 받는다.
     * 빈 곳이 너무 크면(오래 안 들어온 방) 캐시를 버리고 최신 페이지부터 다시 받는다.
     */
    suspend fun refresh(groupId: Long): ApiResult<Unit> {
        val maxSeq = dao.maxSeq(groupId) ?: return loadLatest(groupId)
        return syncAfter(groupId, maxSeq)
    }

    /** 위로 스크롤. 더 오래된 메시지가 남아 있으면 true */
    suspend fun loadOlder(groupId: Long): ApiResult<Boolean> {
        val minSeq = dao.minSeq(groupId) ?: return loadLatest(groupId).map { true }
        if (minSeq <= 1) return ApiResult.Success(false)
        return caller.call { api.messages(groupId, beforeSeq = minSeq, size = PAGE_SIZE) }.map { page ->
            store(page.items)
            page.hasMore
        }
    }

    suspend fun send(groupId: Long, body: String, replyTo: ChatMessage?) {
        val entity = pendingEntity(groupId, MessageType.TEXT, body = body, replyTo = replyTo)
        dao.upsert(listOf(entity))
        trySend(entity)
    }

    /**
     * 사진·동영상 보내기 (CHAT-05). 사진은 한 묶음(IMAGE, 30장까지), 동영상은 하나씩(VIDEO) 메시지를 만든다 — 서버 규칙.
     * 캐시에 먼저 넣어 "올리는 중"으로 보이고, 업로드는 [UploadScheduler]가 맡는다. 다 올리면 [sendIfReady]가 보낸다.
     */
    suspend fun sendMedia(groupId: Long, items: List<LocalMedia>) {
        val images = items.filter { it.kind == MediaKind.IMAGE }
        val videos = items.filter { it.kind == MediaKind.VIDEO }
        val messages = buildList {
            if (images.isNotEmpty()) add(pendingEntity(groupId, MessageType.IMAGE, media = images, order = size.toLong()))
            videos.forEach { add(pendingEntity(groupId, MessageType.VIDEO, media = listOf(it), order = size.toLong())) }
        }
        dao.upsert(messages)
        messages.forEach { uploadScheduler.schedule(it.key) }
    }

    /** 업로드가 끝난 사진·동영상 메시지를 보낸다. 연결이 없으면 대기열에 남아 연결될 때 보낸다 */
    suspend fun sendIfReady(key: String) {
        val entity = dao.get(key)?.takeIf { it.seq == null && it.sendState == SendState.SENDING.name } ?: return
        trySend(entity)
    }

    /** 실패한 메시지를 같은 clientMessageId로 다시 보낸다. 덜 올린 파일이 있으면 업로드부터 다시 */
    suspend fun retry(key: String) {
        val entity = dao.get(key)?.takeIf { it.seq == null } ?: return
        dao.updateSendState(key, SendState.SENDING.name)
        if (entity.hasPendingUploads()) uploadScheduler.schedule(key) else trySend(entity)
    }

    /** 보내지 못한 메시지를 지운다 */
    suspend fun discard(key: String) {
        uploadScheduler.cancel(key)
        dao.deletePending(key)
    }

    /**
     * 연결되면 대기 중인 메시지를 만든 순서대로 보낸다. 서버 전송 제한(초당 5개)에 걸리지 않게 간격을 둔다.
     * 아직 올리는 중인 사진·동영상 메시지는 건너뛴다 (업로드가 끝나면 [sendIfReady]).
     */
    suspend fun flushPending(pause: suspend () -> Unit) {
        dao.pending().filterNot { it.hasPendingUploads() }.forEach { entity ->
            if (!trySend(entity)) return
            pause()
        }
    }

    /** 끝까지 읽었다. 연결돼 있으면 STOMP로, 아니면 REST로 알린다 */
    suspend fun markRead(groupId: Long) {
        val lastSeq = dao.maxSeq(groupId) ?: return
        groupRepository.clearUnread(groupId)
        readLock.withLock {
            if ((sentReadSeq[groupId] ?: -1) >= lastSeq) return
            val sent = socket.sendRead(groupId, lastSeq) ||
                caller.call { api.read(groupId, ReadRequest(lastSeq)) } is ApiResult.Success
            if (sent) sentReadSeq[groupId] = lastSeq
        }
    }

    /** 보낸 사람만, 보낸 지 5분 안에 (CHAT-06) */
    fun canDelete(message: ChatMessage, myUserId: Long?): Boolean =
        message.id != null && !message.deleted && !message.isSystem && message.senderId == myUserId &&
            Duration.between(message.sentAt, clock.instant()) < DELETE_WINDOW

    suspend fun delete(message: ChatMessage): ApiResult<Unit> {
        val id = message.id ?: return ApiResult.Success(Unit)
        return caller.call { api.delete(id) }.also {
            if (it is ApiResult.Success) {
                dao.markDeleted(message.groupId, id)
                message.seq?.let { seq -> groupRepository.applyDeleted(message.groupId, seq) }
            }
        }
    }

    /** 내가 이미 고른 공감을 다시 누르면 취소, 다른 공감이면 바꾼다 (한 사람당 1개) */
    suspend fun toggleReaction(message: ChatMessage, emoji: ReactionEmoji): ApiResult<Unit> {
        val id = message.id ?: return ApiResult.Success(Unit)
        val me = sessionStore.userId()
        val mine = message.reactions.firstOrNull { me in it.userIds }?.emoji
        val result = caller.call {
            if (mine == emoji) api.unreact(id) else api.react(id, ReactionRequest(emoji.name))
        }
        if (result is ApiResult.Success) applyReactions(result.value)
        return result.map { }
    }

    /** 다시 연결됐을 때 캐시가 있는 그룹은 놓친 메시지를 채운다 */
    suspend fun syncCached(groupIds: Collection<Long>) {
        groupIds.forEach { groupId -> dao.maxSeq(groupId)?.let { syncAfter(groupId, it) } }
    }

    /** /sub/groups/{groupId} 이벤트 */
    suspend fun onGroupEvent(groupId: Long, event: ChatEventEnvelope) {
        when (event.type) {
            ChatEventTypes.MESSAGE_CREATED -> event.decode(MessageResponse.serializer())?.let { onMessageCreated(it) }
            ChatEventTypes.MESSAGE_DELETED -> event.decode(MessageDeleted.serializer())?.let {
                dao.markDeleted(it.groupId, it.id)
                groupRepository.applyDeleted(it.groupId, it.seq)
            }
            ChatEventTypes.READ_UPDATED -> event.decode(ReadUpdated.serializer())?.let { read ->
                dao.decrementUnread(read.groupId, read.fromSeq, read.lastReadSeq, read.userId)
                // 내 다른 기기에서 읽었다
                if (read.userId == sessionStore.userId()) groupRepository.clearUnread(groupId)
            }
            ChatEventTypes.REACTION_UPDATED -> event.decode(ReactionsResponse.serializer())?.let { applyReactions(it) }
            ChatEventTypes.MEDIA_UPDATED -> event.decode(MediaUpdated.serializer())?.let { applyMediaUpdate(it) }
        }
    }

    /**
     * /user/queue/events 개인 이벤트. 재전송 확인(message.created)은 캐시에 넣고, 전송 실패는 FAILED로 바꾼다.
     * 전송 제한(RATE_LIMITED)은 SENDING으로 두고 true를 돌려준다 → 잠시 뒤 [flushPending].
     */
    suspend fun onUserEvent(event: ChatEventEnvelope): Boolean {
        when (event.type) {
            ChatEventTypes.MESSAGE_CREATED -> event.decode(MessageResponse.serializer())?.let { onMessageCreated(it) }
            ChatEventTypes.ERROR -> {
                val error = event.decode(ChatErrorEvent.serializer()) ?: return false
                val clientMessageId = error.clientMessageId ?: return false
                if (error.code == ErrorCodes.RATE_LIMITED) return true
                dao.updateSendState(clientMessageId, SendState.FAILED.name)
            }
        }
        return false
    }

    suspend fun clear() {
        dao.clear()
        readLock.withLock { sentReadSeq.clear() }
    }

    private suspend fun onMessageCreated(message: MessageResponse) {
        val me = sessionStore.userId()
        val type = MessageType.of(message.type)
        groupRepository.applyNewMessage(
            groupId = message.groupId,
            message = LastMessage(
                message.seq, message.senderId, type, message.body, message.deleted, parseServerTime(message.sentAt),
            ),
            countsAsUnread = message.senderId != null && message.senderId != me && type != MessageType.SYSTEM,
        )
        // 들어온 사람·나간 사람이 바뀌었다 → 구성원 이름·수를 새로 받는다
        if (type == MessageType.SYSTEM) {
            groupRepository.detail(message.groupId)
            groupRepository.refreshGroups()
        }

        val maxSeq = dao.maxSeq(message.groupId)
        when {
            // 캐시가 없는 방은 들어갈 때 최신 페이지를 받는다. 내가 보낸 메시지는 SENDING 줄을 바꿔야 하므로 넣는다
            maxSeq == null && message.senderId != me -> Unit
            maxSeq != null && message.seq > maxSeq + 1 -> syncAfter(message.groupId, maxSeq)
            else -> store(listOf(message))
        }
    }

    private suspend fun loadLatest(groupId: Long): ApiResult<Unit> =
        caller.call { api.messages(groupId, size = PAGE_SIZE) }.map { store(it.items) }

    /** afterSeq로 끝까지 받는다. [MAX_SYNC_PAGES]를 넘으면 캐시를 버리고 최신 페이지만 받는다 */
    private suspend fun syncAfter(groupId: Long, fromSeq: Long): ApiResult<Unit> {
        var after = fromSeq
        repeat(MAX_SYNC_PAGES) {
            val result = caller.call { api.messages(groupId, afterSeq = after, size = PAGE_SIZE) }
            if (result !is ApiResult.Success) return result.map { }
            store(result.value.items)
            if (!result.value.hasMore) return ApiResult.Success(Unit)
            after = result.value.items.lastOrNull()?.seq ?: return ApiResult.Success(Unit)
        }
        dao.clearSent(groupId)
        return loadLatest(groupId)
    }

    private suspend fun store(messages: List<MessageResponse>) {
        if (messages.isEmpty()) return
        dao.upsert(messages.map { it.toEntity() })
    }

    private fun trySend(entity: MessageEntity): Boolean {
        val mediaIds = entity.localMediaJson?.let { decode(LOCAL_MEDIA_SERIALIZER, it) }?.mapNotNull { it.mediaId }
        val request = SendMessageRequest(
            clientMessageId = entity.clientMessageId ?: entity.key,
            type = entity.type,
            body = entity.body,
            mediaIds = mediaIds,
            replyToId = entity.replyToId,
        )
        return socket.send(entity.groupId, request)
    }

    private fun MessageEntity.hasPendingUploads(): Boolean =
        localMediaJson?.let { decode(LOCAL_MEDIA_SERIALIZER, it) }?.any { it.mediaId == null } == true

    private suspend fun pendingEntity(
        groupId: Long,
        type: MessageType,
        body: String? = null,
        replyTo: ChatMessage? = null,
        media: List<LocalMedia>? = null,
        order: Long = 0,
    ): MessageEntity {
        val now = clock.millis()
        val clientMessageId = UUID.randomUUID().toString()
        return MessageEntity(
            key = clientMessageId,
            groupId = groupId,
            serverId = null,
            seq = null,
            senderId = sessionStore.userId(),
            clientMessageId = clientMessageId,
            type = type.name,
            body = body,
            replyToId = replyTo?.id,
            replyJson = replyTo?.toReplyJson(),
            reactionsJson = EMPTY_LIST_JSON,
            unreadCount = 0,
            deleted = false,
            sentAtMillis = now,
            sendState = SendState.SENDING.name,
            // 한 번에 고른 사진 묶음·동영상은 같은 시각이라 순서를 덧붙인다
            localOrder = now * 100 + order,
            localMediaJson = media?.let { list -> encode(LOCAL_MEDIA_SERIALIZER, list.map { it.toJson() }) },
        )
    }

    /** media.updated: 처리 끝난 사진·동영상 하나를 그 메시지에서 바꾼다 */
    private suspend fun applyMediaUpdate(update: MediaUpdated) {
        val entity = dao.getByServerId(update.messageId) ?: return
        val media = decode(MEDIA_SERIALIZER, entity.mediaJson).orEmpty()
            .map { if (it.id == update.media.id) update.media else it }
        dao.updateMedia(update.messageId, encode(MEDIA_SERIALIZER, media))
    }

    private suspend fun applyReactions(response: ReactionsResponse) =
        dao.updateReactions(response.messageId, encode(REACTIONS_SERIALIZER, response.reactions))

    private fun MessageResponse.toEntity() = MessageEntity(
        key = clientMessageId ?: "id:$id",
        groupId = groupId,
        serverId = id,
        seq = seq,
        senderId = senderId,
        clientMessageId = clientMessageId,
        type = type,
        body = body,
        replyToId = replyTo?.id,
        replyJson = replyTo?.let { encode(ReplyPreviewResponse.serializer(), it) },
        reactionsJson = encode(REACTIONS_SERIALIZER, reactions),
        unreadCount = unreadCount,
        deleted = deleted,
        sentAtMillis = parseServerTime(sentAt).toEpochMilli(),
        sendState = SendState.SENT.name,
        localOrder = 0,
        mediaJson = encode(MEDIA_SERIALIZER, media),
    )

    private fun MessageEntity.toModel() = ChatMessage(
        key = key,
        groupId = groupId,
        id = serverId,
        seq = seq,
        senderId = senderId,
        clientMessageId = clientMessageId,
        type = MessageType.of(type),
        body = body,
        replyTo = replyJson?.let { decode(ReplyPreviewResponse.serializer(), it) }?.toModel(),
        reactions = decode(REACTIONS_SERIALIZER, reactionsJson)?.toReactions().orEmpty(),
        unreadCount = unreadCount,
        deleted = deleted,
        sentAt = Instant.ofEpochMilli(sentAtMillis),
        sendState = SendState.entries.firstOrNull { it.name == sendState } ?: SendState.SENT,
        media = decode(MEDIA_SERIALIZER, mediaJson).orEmpty().map { it.toModel() },
        localMedia = localMediaJson?.let { decode(LOCAL_MEDIA_SERIALIZER, it) }.orEmpty().map { it.toModel() },
    )

    private fun ChatMessage.toReplyJson(): String? {
        val id = id ?: return null
        val seq = seq ?: return null
        return encode(
            ReplyPreviewResponse.serializer(),
            ReplyPreviewResponse(id, seq, senderId, type.name, body, deleted),
        )
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

    private fun <T> decode(serializer: KSerializer<T>, value: String): T? = try {
        json.decodeFromString(serializer, value)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun <T> ChatEventEnvelope.decode(serializer: KSerializer<T>): T? {
        val element: JsonElement = data ?: return null
        return try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        const val PAGE_SIZE = 50
        private const val MAX_SYNC_PAGES = 5
        private const val EMPTY_LIST_JSON = "[]"
        val DELETE_WINDOW: Duration = Duration.ofMinutes(5)
        private val REACTIONS_SERIALIZER = ListSerializer(ReactionResponse.serializer())
        private val MEDIA_SERIALIZER = ListSerializer(MediaResponse.serializer())
        private val LOCAL_MEDIA_SERIALIZER = ListSerializer(LocalMediaJson.serializer())
    }
}
