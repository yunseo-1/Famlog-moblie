package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.Todo
import com.famlog.core.model.TodoEdit
import com.famlog.core.model.TodoPage
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.CreateTodoRequest
import com.famlog.core.network.TodoApi
import com.famlog.core.network.donePatch
import com.famlog.core.network.toModel
import com.famlog.core.network.toPatch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 할 일 (TODO-01). 일정처럼 로컬 캐시 없이 화면에 들어올 때 받는다 */
@Singleton
class TodoRepository @Inject constructor(
    private val api: TodoApi,
    private val caller: ApiCaller,
) {
    suspend fun page(groupId: Long, cursor: String? = null): ApiResult<TodoPage> =
        caller.call { api.list(groupId, cursor, PAGE_SIZE).toModel() }

    suspend fun create(groupId: Long, title: String, assigneeId: Long?, dueDate: LocalDate?): ApiResult<Todo> =
        caller.call { api.create(groupId, CreateTodoRequest(title, assigneeId, dueDate?.toString())).toModel() }

    suspend fun edit(todo: Todo, edit: TodoEdit): ApiResult<Todo> =
        caller.call { api.update(todo.id, edit.toPatch(todo.version)).toModel() }

    suspend fun setDone(todo: Todo, done: Boolean, announce: Boolean): ApiResult<Todo> =
        caller.call { api.update(todo.id, donePatch(todo.version, done, announce)).toModel() }

    suspend fun delete(todo: Todo): ApiResult<Unit> = caller.call { api.delete(todo.id) }

    private companion object {
        /** 서버 최대 100 */
        const val PAGE_SIZE = 100
    }
}
