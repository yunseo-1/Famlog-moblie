package com.famlog.core.network

import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateDetail
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateEvidence
import com.famlog.core.model.CandidateFields
import com.famlog.core.model.CandidateLocation
import com.famlog.core.model.CandidatePage
import com.famlog.core.model.CandidateResolved
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.CandidateSurface
import com.famlog.core.model.CandidateType
import com.famlog.core.model.CandidateTarget
import com.famlog.core.model.ItemSource
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceOption
import com.famlog.core.model.ScheduleTiming
import com.famlog.core.model.TargetAction
import com.famlog.core.model.TargetItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate
import java.time.LocalTime

/**
 * 등록 후보 (ARC-05). 승인·무시는 조회한 version을 보낸다.
 * 다른 가족이 먼저 처리했으면 409 CANDIDATE_ALREADY_DECIDED, 만료 409 CANDIDATE_EXPIRED,
 * 채울 항목이 있으면 422 CANDIDATE_MISSING_FIELDS(fieldErrors). 같은 사람이 다시 보내면 같은 결과(재시도 안전).
 */
interface CandidateApi {
    /** 최신 순. [status]는 쉼표로 여러 개, 기본 PENDING(만료 제외). [fromSeq]~[toSeq]는 카드를 붙일 메시지 범위 */
    @GET("groups/{groupId}/candidates")
    suspend fun list(
        @Path("groupId") groupId: Long,
        @Query("status") status: String? = null,
        @Query("fromSeq") fromSeq: Long? = null,
        @Query("toSeq") toSeq: Long? = null,
        @Query("cursor") cursor: String? = null,
        @Query("size") size: Int? = null,
    ): CandidateListResponse

    @GET("candidates/{candidateId}")
    suspend fun detail(@Path("candidateId") candidateId: Long): CandidateDetailResponse

    @POST("candidates/{candidateId}/approve")
    suspend fun approve(@Path("candidateId") candidateId: Long, @Body body: ApproveRequest): DecisionResponse

    @POST("candidates/{candidateId}/dismiss")
    suspend fun dismiss(@Path("candidateId") candidateId: Long, @Body body: DismissRequest): DecisionResponse

    /** 승인한 사람·관리자 이상, 승인 후 5분 안 (아니면 403 CANDIDATE_NOT_UNDOABLE) */
    @POST("candidates/{candidateId}/undo")
    suspend fun undo(@Path("candidateId") candidateId: Long): DecisionResponse
}

/** 값을 하나도 넣지 않으면 후보 값 그대로 등록 */
@Serializable
data class ApproveRequest(
    val version: Long,
    val schedule: ScheduleRequest? = null,
    val todo: CreateTodoRequest? = null,
    val place: ApprovePlaceRequest? = null,
    /** 변경·완료 후보: target.choices 중 고른 entityId */
    val targetId: Long? = null,
)

@Serializable
data class ApprovePlaceRequest(val kakaoPlaceId: String, val category: String, val oneLineReview: String? = null)

@Serializable
data class DismissRequest(val version: Long, val reason: String? = null)

@Serializable
data class CandidateResponse(
    val id: Long,
    val groupId: Long,
    val type: String,
    val status: String,
    val fields: JsonObject = JsonObject(emptyMap()),
    val inferred: List<String> = emptyList(),
    val missingFields: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val surface: String? = null,
    val anchorMessageId: Long? = null,
    val anchorSeq: Long? = null,
    val version: Long,
    val decidedBy: Long? = null,
    val decidedAt: String? = null,
    val entityType: String? = null,
    val entityId: Long? = null,
    val linkedExisting: Boolean = false,
    val expiresAt: String? = null,
    val createdAt: String,
    val target: JsonObject? = null,
)

@Serializable
data class CandidateListResponse(val items: List<CandidateResponse>, val nextCursor: String? = null)

@Serializable
data class EvidenceResponse(
    val messageId: Long,
    val seq: Long,
    val senderId: Long? = null,
    val sentAt: String,
    val spans: List<String> = emptyList(),
    val text: String? = null,
)

@Serializable
data class CandidateDetailResponse(val candidate: CandidateResponse, val evidence: List<EvidenceResponse> = emptyList())

@Serializable
data class DecisionResponse(val candidate: CandidateResponse)

/** 채팅에서 등록한 항목의 원문 (일정·할 일·장소 응답의 source, ARC-06). [candidateId]는 백엔드가 붙이면 온다 */
@Serializable
data class SourceResponse(val messageId: Long, val seq: Long, val candidateId: Long? = null)

fun SourceResponse.toModel() = ItemSource(messageId, seq, candidateId)

/** WebSocket candidate.resolved */
@Serializable
data class CandidateResolvedEvent(
    val groupId: Long,
    val candidateId: Long,
    val status: String,
    val decidedBy: Long? = null,
    val decidedByName: String? = null,
    val entityType: String? = null,
    val entityId: Long? = null,
    val linkedExisting: Boolean = false,
)

/** 모르는 유형·읽지 못한 대상은 null (앱이 아직 못 그리는 후보. 새 항목 카드로 잘못 보이지 않게 뺀다) */
fun CandidateResponse.toModel(): Candidate? {
    val type = CandidateType.entries.firstOrNull { it.name == type } ?: return null
    val target = target?.let { it.toTarget() ?: return null }
    return Candidate(
        id = id,
        groupId = groupId,
        type = type,
        status = CandidateStatus.of(status),
        fields = fields.toFields(type),
        inferred = inferred.toSet(),
        missingFields = missingFields.toSet(),
        confidence = confidence,
        surface = if (surface == CandidateSurface.CARD.name) CandidateSurface.CARD else CandidateSurface.INBOX,
        anchorMessageId = anchorMessageId,
        anchorSeq = anchorSeq,
        version = version,
        decidedBy = decidedBy,
        decidedAt = decidedAt?.let(::parseServerTime),
        entityType = entityOf(entityType),
        entityId = entityId,
        linkedExisting = linkedExisting,
        expiresAt = expiresAt?.let(::parseServerTime),
        createdAt = parseServerTime(createdAt),
        target = target,
    )
}

fun CandidateListResponse.toModel() = CandidatePage(items.mapNotNull { it.toModel() }, nextCursor)

fun CandidateDetailResponse.toModel(): CandidateDetail? = candidate.toModel()?.let { model ->
    CandidateDetail(
        candidate = model,
        evidence = evidence.map {
            CandidateEvidence(it.messageId, it.seq, it.senderId, parseServerTime(it.sentAt), it.spans, it.text)
        },
    )
}

fun CandidateResolvedEvent.toModel() = CandidateResolved(
    groupId = groupId,
    candidateId = candidateId,
    status = CandidateStatus.of(status),
    decidedBy = decidedBy,
    decidedByName = decidedByName,
    entityType = entityOf(entityType),
    entityId = entityId,
    linkedExisting = linkedExisting,
)

private fun entityOf(value: String?): CandidateEntity? = CandidateEntity.entries.firstOrNull { it.name == value }

private const val ALL_MEMBERS = "ALL"

/** 형식이 어긋난 값은 비운다 (서버가 원본, 앱은 그릴 수 있는 만큼만) */
private fun JsonObject.toFields(type: CandidateType): CandidateFields = when (type) {
    CandidateType.SCHEDULE -> CandidateFields.Schedule(
        title = string("title"),
        date = string("date")?.let(::parseDate),
        startTime = string("startTime")?.let(::parseTime),
        endTime = string("endTime")?.let(::parseTime),
        location = (this["location"] as? JsonObject)?.let {
            CandidateLocation(it.string("text"), it.long("aliasId"), it.long("placeId"))
        },
        attendeeIds = ids("attendeeIds"),
        allMembers = hasAll("attendeeIds"),
        attendeeChoices = choices("attendeeChoices"),
        unmatchedAttendees = strings("unmatchedAttendees"),
    )
    CandidateType.ANNIVERSARY -> CandidateFields.Anniversary(
        title = string("title"),
        month = int("month"),
        day = int("day"),
        personText = string("personText"),
    )
    CandidateType.TODO -> CandidateFields.Todo(
        title = string("title"),
        dueDate = string("dueDate")?.let(::parseDate),
        assigneeIds = ids("assigneeIds"),
        allMembers = hasAll("assigneeIds"),
        assigneeChoices = choices("assigneeChoices"),
        unmatchedAssignees = strings("unmatchedAssignees"),
    )
    CandidateType.PLACE -> CandidateFields.Place(
        name = string("name"),
        regionHint = string("regionHint"),
        kakaoPlaceId = string("kakaoPlaceId"),
        options = (this["options"] as? JsonArray).orEmpty().mapNotNull { option ->
            val o = option as? JsonObject ?: return@mapNotNull null
            PlaceOption(
                kakaoPlaceId = o.string("kakaoPlaceId") ?: return@mapNotNull null,
                name = o.string("name") ?: return@mapNotNull null,
                category = PlaceCategory.of(o.string("category")),
                categoryDetail = o.string("categoryDetail"),
                address = o.string("address"),
                region1 = o.string("region1"),
                region2 = o.string("region2"),
            )
        },
        opinion = string("opinion"),
        recommendedBy = long("recommendedBy"),
    )
}

/** target: {entityType, action, entityId?, title?, createdBy?, before?, after?, choices[]} */
private fun JsonObject.toTarget(): CandidateTarget? {
    val entity = entityOf(string("entityType"))?.takeIf { it != CandidateEntity.PLACE } ?: return null
    val action = TargetAction.entries.firstOrNull { it.name == string("action") } ?: return null
    val item = toTargetItem()
    val choices = (this["choices"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toTargetItem() }
    if (item == null && choices.isEmpty()) return null
    return CandidateTarget(entity, action, item, choices)
}

private fun JsonObject.toTargetItem(): TargetItem? {
    val id = long("entityId") ?: return null
    val before = this["before"] as? JsonObject
    return TargetItem(
        entityId = id,
        title = string("title"),
        createdBy = long("createdBy"),
        before = before?.toTiming(),
        after = (this["after"] as? JsonObject)?.toTiming(),
        dueDate = before?.string("dueDate")?.let(::parseDate),
        assigneeId = before?.long("assigneeId"),
    )
}

/** 일정 before·after. 날짜가 없으면(할 일) null */
private fun JsonObject.toTiming(): ScheduleTiming? {
    val date = string("date")?.let(::parseDate) ?: return null
    val start = string("startTime")?.let(::parseTime)
    val allDay = this["allDay"].primitive()?.takeIf { !it.isString }?.booleanOrNull ?: (start == null)
    return ScheduleTiming(date, start.takeUnless { allDay }, string("endTime")?.let(::parseTime).takeUnless { allDay }, allDay)
}

private fun JsonElement?.primitive(): JsonPrimitive? = this as? JsonPrimitive

private fun JsonObject.string(key: String): String? =
    this[key].primitive()?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.long(key: String): Long? = this[key].primitive()?.takeIf { !it.isString }?.longOrNull

private fun JsonObject.int(key: String): Int? = this[key].primitive()?.takeIf { !it.isString }?.intOrNull

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { it.primitive()?.takeIf { p -> p.isString }?.contentOrNull }

/** 숫자 ID만 ("ALL"은 [hasAll]) */
private fun JsonObject.ids(key: String): List<Long> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { it.primitive()?.takeIf { p -> !p.isString }?.longOrNull }

private fun JsonObject.hasAll(key: String): Boolean =
    (this[key] as? JsonArray).orEmpty().any { it.primitive()?.let { p -> p.isString && p.content == ALL_MEMBERS } == true }

private fun JsonObject.choices(key: String): List<List<Long>> = (this[key] as? JsonArray).orEmpty().mapNotNull { group ->
    (group as? JsonArray)?.mapNotNull { it.primitive()?.longOrNull }?.takeIf { it.isNotEmpty() }
}

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
