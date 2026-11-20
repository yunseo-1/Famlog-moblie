package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateApproval
import com.famlog.core.model.CandidateDetail
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.DismissReason
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.ApprovePlaceRequest
import com.famlog.core.network.ApproveRequest
import com.famlog.core.network.CandidateApi
import com.famlog.core.network.CandidateResolvedEvent
import com.famlog.core.network.CandidateResponse
import com.famlog.core.network.ChatEventEnvelope
import com.famlog.core.network.ChatEventTypes
import com.famlog.core.network.CreateTodoRequest
import com.famlog.core.network.DismissRequest
import com.famlog.core.network.toModel
import com.famlog.core.network.toRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 등록 후보 (ARC-05). 채팅방에 붙일 대기·승인 후보를 그룹별로 메모리에 들고(서버가 원본),
 * 승인·무시·되돌리기 결과와 WebSocket candidate.* 이벤트를 반영한다.
 */
@Singleton
class CandidateRepository @Inject constructor(
    private val api: CandidateApi,
    private val caller: ApiCaller,
    private val json: Json,
    private val clock: Clock,
) {
    /** groupId → (candidateId → 후보) */
    private val store = MutableStateFlow<Map<Long, Map<Long, Candidate>>>(emptyMap())

    /** groupId → 제안함 다음 쪽 커서 (없으면 끝까지 받았다) */
    private val inboxCursors = MutableStateFlow<Map<Long, String?>>(emptyMap())

    /** 이 그룹 후보 (만료·무시·되돌린 것 포함, 화면이 상태로 거른다) */
    fun candidates(groupId: Long): Flow<List<Candidate>> =
        store.map { it[groupId]?.values?.toList().orEmpty() }.distinctUntilChanged()

    /** 제안함(SCR-14)에 더 받을 쪽이 있는지. 아직 한 번도 안 받았으면 false */
    fun inboxHasMore(groupId: Long): Flow<Boolean> = inboxCursors.map { it[groupId] != null }.distinctUntilChanged()

    /**
     * 제안함: 대기 후보(신뢰도 구간 무관, 만료 제외)를 최신 순으로 한 쪽씩 받는다. [more]면 다음 쪽.
     * 첫 쪽을 다시 받으면 그 쪽 범위에서 응답에 없는 대기 후보(그새 처리됨)는 뺀다.
     */
    suspend fun loadInbox(groupId: Long, more: Boolean = false): ApiResult<Unit> {
        val cursor = if (more) inboxCursors.value[groupId] ?: return ApiResult.Success(Unit) else null
        val result = caller.call { api.list(groupId, INBOX_STATUS, cursor = cursor, size = PAGE_SIZE) }
        if (result !is ApiResult.Success) return result.map { }
        val page = result.value.toModel()
        store.update { all ->
            var group = all[groupId].orEmpty()
            if (!more) {
                // 첫 쪽보다 오래된 후보는 아직 모르므로 남긴다
                val oldest = if (page.nextCursor == null) null else page.items.minOfOrNull { it.createdAt }
                val ids = page.items.map { it.id }.toSet()
                group = group.filterValues { c ->
                    c.status != CandidateStatus.PENDING || c.id in ids || (oldest != null && c.createdAt < oldest)
                }
            }
            all + (groupId to group + page.items.associateBy { it.id })
        }
        inboxCursors.update { it + (groupId to page.nextCursor) }
        return ApiResult.Success(Unit)
    }

    /**
     * 채팅방에 보이는 메시지 범위([fromSeq]~[toSeq], null이면 끝까지)의 대기·승인 후보를 끝까지 받는다.
     * 그 범위에서 응답에 없는 후보(그새 무시·만료)는 뺀다.
     */
    suspend fun loadForChat(groupId: Long, fromSeq: Long?, toSeq: Long?): ApiResult<Unit> {
        val loaded = mutableListOf<Candidate>()
        var cursor: String? = null
        do {
            val result = caller.call { api.list(groupId, CHAT_STATUSES, fromSeq, toSeq, cursor, PAGE_SIZE) }
            if (result !is ApiResult.Success) return result.map { }
            loaded += result.value.toModel().items
            cursor = result.value.nextCursor
        } while (cursor != null)
        store.update { all ->
            val kept = all[groupId].orEmpty().filterValues { !it.inRange(fromSeq, toSeq) }
            all + (groupId to kept + loaded.associateBy { it.id })
        }
        return ApiResult.Success(Unit)
    }

    suspend fun detail(candidateId: Long): ApiResult<CandidateDetail> =
        caller.call { api.detail(candidateId) }.mapNotNull { it.toModel() }
            .also { if (it is ApiResult.Success) upsert(it.value.candidate) }

    /** [approval]이 null이면 후보 값 그대로 ([등록]) */
    suspend fun approve(candidate: Candidate, approval: CandidateApproval?): ApiResult<Candidate> {
        val body = ApproveRequest(
            version = candidate.version,
            schedule = (approval as? CandidateApproval.Schedule)?.draft?.toRequest(),
            todo = (approval as? CandidateApproval.Todo)?.let {
                CreateTodoRequest(it.title.trim(), it.assigneeId, it.dueDate?.toString())
            },
            place = (approval as? CandidateApproval.Place)?.let {
                ApprovePlaceRequest(it.kakaoPlaceId, it.category.name, it.oneLineReview?.trim()?.takeIf(String::isNotEmpty))
            },
            targetId = (approval as? CandidateApproval.Target)?.targetId,
        )
        return decide { api.approve(candidate.id, body).candidate }
    }

    suspend fun dismiss(candidate: Candidate, reason: DismissReason?): ApiResult<Candidate> =
        decide { api.dismiss(candidate.id, DismissRequest(candidate.version, reason?.name)).candidate }

    suspend fun undo(candidate: Candidate): ApiResult<Candidate> = decide { api.undo(candidate.id).candidate }

    /** /sub/groups/{groupId} 이벤트 중 candidate.* 만 처리한다 */
    fun onGroupEvent(groupId: Long, event: ChatEventEnvelope) {
        when (event.type) {
            ChatEventTypes.CANDIDATE_CREATED -> event.decode(CandidateResponse.serializer())?.toModel()?.let { upsert(it) }
            ChatEventTypes.CANDIDATE_RESOLVED -> event.decode(CandidateResolvedEvent.serializer())?.toModel()?.let { resolved ->
                store.update { all ->
                    val group = all[groupId] ?: return@update all
                    val current = group[resolved.candidateId] ?: return@update all
                    val updated = current.copy(
                        status = resolved.status,
                        decidedBy = resolved.decidedBy,
                        // 되돌리기 5분은 승인한 사람만 보므로, 이벤트에 없는 시각은 받은 때로 둔다
                        decidedAt = if (current.status == resolved.status) current.decidedAt else clock.instant(),
                        entityType = resolved.entityType,
                        entityId = resolved.entityId,
                        linkedExisting = resolved.linkedExisting,
                    )
                    all + (groupId to group + (updated.id to updated))
                }
            }
        }
    }

    fun clear() {
        store.value = emptyMap()
        inboxCursors.value = emptyMap()
    }

    private suspend fun decide(block: suspend () -> CandidateResponse): ApiResult<Candidate> =
        caller.call(block).mapNotNull { it.toModel() }.also { if (it is ApiResult.Success) upsert(it.value) }

    private fun upsert(candidate: Candidate) = store.update { all ->
        all + (candidate.groupId to all[candidate.groupId].orEmpty() + (candidate.id to candidate))
    }

    private fun Candidate.inRange(fromSeq: Long?, toSeq: Long?): Boolean {
        val seq = anchorSeq ?: return false
        return (fromSeq == null || seq >= fromSeq) && (toSeq == null || seq <= toSeq) &&
            (status == CandidateStatus.PENDING || status == CandidateStatus.APPROVED)
    }

    private fun <T> ChatEventEnvelope.decode(serializer: KSerializer<T>): T? {
        val element = data ?: return null
        return try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val CHAT_STATUSES = "PENDING,APPROVED"
        const val INBOX_STATUS = "PENDING"
        const val PAGE_SIZE = 50
    }
}

/** 응답을 모델로 못 바꾸면(모르는 유형) 형식 오류로 본다 */
private fun <T, R> ApiResult<T>.mapNotNull(transform: (T) -> R?): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(value)?.let { ApiResult.Success(it) } ?: ApiResult.Failure(status = -1, error = null)
    is ApiResult.Failure -> this
    ApiResult.NetworkError -> ApiResult.NetworkError
}
