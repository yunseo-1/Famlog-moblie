package com.famlog.core.network

import com.famlog.core.model.Todo
import com.famlog.core.model.TodoEdit
import com.famlog.core.model.TodoPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate

/**
 * 할 일 (TODO-01). 제목·담당자·기한은 등록한 사람 또는 관리자 이상(403), 완료는 구성원 누구나.
 * 다른 그룹 id는 404, version이 다르면 409 VERSION_CONFLICT.
 */
interface TodoApi {
    /** 안 한 일(기한 이른 순, 기한 없음은 뒤) → 완료한 일(최근 완료 순) */
    @GET("groups/{groupId}/todos")
    suspend fun list(
        @Path("groupId") groupId: Long,
        @Query("cursor") cursor: String? = null,
        @Query("size") size: Int? = null,
    ): TodoListResponse

    @POST("groups/{groupId}/todos")
    suspend fun create(@Path("groupId") groupId: Long, @Body body: CreateTodoRequest): TodoResponse

    /**
     * 보낸 키만 바뀐다. `assigneeId`·`dueDate`는 null을 보내면 지우고 키를 빼면 그대로라,
     * Json 설정(explicitNulls=false)에 맡기지 않고 [JsonObject]로 직접 만든다.
     */
    @PATCH("todos/{todoId}")
    suspend fun update(@Path("todoId") todoId: Long, @Body body: JsonObject): TodoResponse

    @DELETE("todos/{todoId}")
    suspend fun delete(@Path("todoId") todoId: Long)
}

@Serializable
data class CreateTodoRequest(val title: String, val assigneeId: Long? = null, val dueDate: String? = null)

@Serializable
data class TodoResponse(
    val id: Long,
    val groupId: Long,
    val title: String,
    val assigneeId: Long? = null,
    val dueDate: String? = null,
    val done: Boolean = false,
    val doneAt: String? = null,
    val doneBy: Long? = null,
    val createdBy: Long? = null,
    val version: Long,
    val source: SourceResponse? = null,
)

@Serializable
data class TodoListResponse(val items: List<TodoResponse>, val nextCursor: String? = null)

fun TodoResponse.toModel() = Todo(
    id = id,
    groupId = groupId,
    title = title,
    assigneeId = assigneeId,
    dueDate = dueDate?.let(LocalDate::parse),
    done = done,
    doneAt = doneAt?.let(::parseServerTime),
    doneBy = doneBy,
    createdBy = createdBy,
    version = version,
    source = source?.toModel(),
)

fun TodoListResponse.toModel() = TodoPage(items.map { it.toModel() }, nextCursor)

/** 제목·담당자·기한 전체를 보낸다 (지운 담당자·기한은 null) */
fun TodoEdit.toPatch(version: Long): JsonObject = buildJsonObject {
    put("version", version)
    put("title", title)
    put("assigneeId", assigneeId?.let(::JsonPrimitive) ?: JsonNull)
    put("dueDate", dueDate?.toString()?.let(::JsonPrimitive) ?: JsonNull)
}

/** 완료·되돌리기. [announce]면 새로 완료할 때 채팅방에 "○○님이 '…' 할 일을 완료했어요." */
fun donePatch(version: Long, done: Boolean, announce: Boolean): JsonObject = buildJsonObject {
    put("version", version)
    put("done", done)
    if (done) put("announce", announce)
}
