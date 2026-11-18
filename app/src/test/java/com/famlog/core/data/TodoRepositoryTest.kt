package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.Todo
import com.famlog.core.model.TodoEdit
import com.famlog.core.model.errorCode
import com.famlog.core.network.TodoApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class TodoRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: TodoRepository

    @Before
    fun setUp() {
        server.start()
        repository = TodoRepository(server.api<TodoApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 목록은_100개씩_받고_다음_커서를_넘긴다() = runTest {
        server.enqueue(jsonResponse("""{"items":[${todoJson(1)}],"nextCursor":"abc"}"""))
        server.enqueue(jsonResponse("""{"items":[${todoJson(2, done = true)}]}"""))

        val first = repository.page(GROUP) as ApiResult.Success
        val second = repository.page(GROUP, "abc") as ApiResult.Success

        assertEquals("/api/v1/groups/$GROUP/todos?size=100", server.takeRequest().path)
        assertEquals("/api/v1/groups/$GROUP/todos?cursor=abc&size=100", server.takeRequest().path)
        assertEquals("abc", first.value.nextCursor)
        assertEquals(LocalDate.of(2026, 11, 21), first.value.items.single().dueDate)
        assertNull(second.value.nextCursor)
        assertEquals(true, second.value.items.single().done)
    }

    @Test
    fun 담당자_기한을_지우면_null을_보낸다() = runTest {
        server.enqueue(jsonResponse(todoJson(1)))

        repository.edit(todo(version = 3), TodoEdit("우유 사 오기", assigneeId = null, dueDate = null))

        val body = body()
        assertEquals(3L, body["version"]!!.jsonPrimitive.content.toLong())
        assertEquals(JsonNull, body["assigneeId"])
        assertEquals(JsonNull, body["dueDate"])
        assertFalse(body.containsKey("done"))
    }

    @Test
    fun 완료는_done과_알림만_보내고_되돌리기는_알림을_빼고_다른_값은_건드리지_않는다() = runTest {
        server.enqueue(jsonResponse(todoJson(1, done = true)))
        server.enqueue(jsonResponse(todoJson(1)))

        repository.setDone(todo(), done = true, announce = true)
        repository.setDone(todo(), done = false, announce = true)

        val done = body()
        assertEquals("true", done["done"]!!.jsonPrimitive.content)
        assertEquals("true", done["announce"]!!.jsonPrimitive.content)
        assertEquals(setOf("version", "done", "announce"), done.keys)
        val undone = body()
        assertEquals(setOf("version", "done"), undone.keys)
    }

    @Test
    fun 새로_만들_때_담당자_기한이_없으면_키를_빼고_충돌은_VERSION_CONFLICT() = runTest {
        server.enqueue(jsonResponse(todoJson(1), code = 201))
        server.enqueue(errorResponse(409, ErrorCodes.VERSION_CONFLICT))

        repository.create(GROUP, "우유 사 오기", assigneeId = null, dueDate = null)
        val conflict = repository.setDone(todo(), done = true, announce = true)

        assertEquals(setOf("title"), body().keys)
        assertEquals(ErrorCodes.VERSION_CONFLICT, conflict.errorCode)
    }

    private fun body(): JsonObject = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun todo(version: Long = 1) = Todo(1, GROUP, "우유", 3, null, false, null, null, 3, version)

    private fun todoJson(id: Long, done: Boolean = false) =
        """{"id":$id,"groupId":$GROUP,"kind":"TASK","title":"우유","assigneeId":3,"dueDate":"2026-11-21","done":$done,"createdBy":3,"version":1}"""

    private companion object {
        const val GROUP = 5L
    }
}
