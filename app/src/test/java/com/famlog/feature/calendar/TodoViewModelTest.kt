package com.famlog.feature.calendar

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.TodoRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Todo
import com.famlog.core.model.TodoEdit
import com.famlog.core.model.TodoPage
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import java.time.LocalDate
import java.time.ZoneId

class TodoViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val members = listOf(
        Member(ME, "윤서", GroupRole.MEMBER, null, "막내", null),
        Member(DAD, "김아빠", GroupRole.OWNER, null, "아빠", null),
    )
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { currentGroupId } returns MutableStateFlow(GROUP)
        every { details } returns MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, members)))
    }
    private val milk = todo(1, "우유 사 오기", assignee = ME, createdBy = DAD)
    private val trash = todo(2, "분리수거", assignee = DAD, createdBy = ME)
    private val doneTodo = todo(3, "택배 받기", assignee = DAD, createdBy = DAD, done = true)
    private val todos = mockk<TodoRepository> {
        coEvery { page(GROUP, null) } returns ApiResult.Success(TodoPage(listOf(milk, trash, doneTodo), nextCursor = null))
    }

    private fun viewModel() = TodoViewModel(todos, groups, FakeSessionStore(userId = ME), clock)

    @Test
    fun 안_한_일과_완료한_일을_나누고_담당자로_거른다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        assertEquals(listOf(milk, trash), viewModel.uiState.value.open)
        assertEquals(listOf(doneTodo), viewModel.uiState.value.done)

        viewModel.toggleMember(DAD)
        assertEquals(listOf(trash), viewModel.uiState.value.open)
        assertEquals(listOf(doneTodo), viewModel.uiState.value.done)
    }

    @Test
    fun 완료는_바로_보이고_채팅방_알림을_켜서_보낸_뒤_다시_받는다() = runTest {
        val response = CompletableDeferred<ApiResult<Todo>>()
        coEvery { todos.setDone(milk, true, true) } coAnswers { response.await() }
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.toggleDone(milk)
        assertTrue(viewModel.uiState.value.done.any { it.id == milk.id })

        response.complete(ApiResult.Success(milk.copy(done = true, version = 2)))
        coVerify { todos.setDone(milk, done = true, announce = true) }
        coVerify(exactly = 2) { todos.page(GROUP, null) }
    }

    @Test
    fun 완료가_실패하면_되돌리고_알린다() = runTest {
        coEvery { todos.setDone(any(), any(), any()) } returns ApiResult.NetworkError
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.events.test {
            viewModel.toggleDone(milk)
            assertEquals(TodoEvent.Message(UiText.Res(R.string.error_network)), awaitItem())
        }
        assertFalse(viewModel.uiState.value.todos.first { it.id == milk.id }.done)
    }

    @Test
    fun 다른_가족이_먼저_고쳤으면_안내하고_목록을_다시_받는다() = runTest {
        coEvery { todos.setDone(any(), any(), any()) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.VERSION_CONFLICT, "충돌", null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.events.test {
            viewModel.toggleDone(trash)
            assertEquals(TodoEvent.Message(UiText.Res(R.string.todo_version_conflict)), awaitItem())
        }
        coVerify(exactly = 2) { todos.page(GROUP, null) }
    }

    @Test
    fun 새_할_일은_담당자가_나이고_제목이_없으면_보내지_않는다() = runTest {
        coEvery { todos.create(GROUP, "우유 사 오기", ME, LocalDate.of(2026, 11, 21)) } returns ApiResult.Success(milk)
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.openNew()
        assertEquals(ME, viewModel.uiState.value.sheet?.assigneeId)
        viewModel.saveSheet()
        assertTrue(viewModel.uiState.value.sheet!!.showErrors)
        coVerify(exactly = 0) { todos.create(any(), any(), any(), any()) }

        viewModel.onSheetTitleChange(" 우유 사 오기 ")
        viewModel.onSheetDueDateChange(LocalDate.of(2026, 11, 21))
        viewModel.saveSheet()

        assertNull(viewModel.uiState.value.sheet)
        coVerify { todos.create(GROUP, "우유 사 오기", ME, LocalDate.of(2026, 11, 21)) }
    }

    @Test
    fun 같은_담당자를_다시_누르면_담당자를_지우고_수정은_전체를_보낸다() = runTest {
        coEvery { todos.edit(trash, TodoEdit("분리수거", null, null)) } returns ApiResult.Success(trash)
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.openEdit(trash)
        viewModel.onSheetAssigneeChange(DAD)
        viewModel.saveSheet()

        coVerify { todos.edit(trash, TodoEdit("분리수거", null, null)) }
    }

    @Test
    fun 제목_담당자_기한은_등록자나_관리자만_고친다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value.canEdit(trash))
        assertFalse(viewModel.uiState.value.canEdit(milk))
    }

    @Test
    fun 삭제하면_목록에서_뺀다() = runTest {
        coEvery { todos.delete(trash) } returns ApiResult.Success(Unit)
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.openEdit(trash)
        viewModel.deleteSheetTodo()

        assertNull(viewModel.uiState.value.sheet)
        assertFalse(viewModel.uiState.value.todos.any { it.id == trash.id })
    }

    @Test
    fun 다음_페이지를_이어_붙인다() = runTest {
        val extra = todo(4, "장보기", assignee = null, createdBy = ME)
        coEvery { todos.page(GROUP, null) } returns ApiResult.Success(TodoPage(listOf(milk), nextCursor = "c1"))
        coEvery { todos.page(GROUP, "c1") } returns ApiResult.Success(TodoPage(listOf(extra), nextCursor = null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(listOf(milk, extra), viewModel.uiState.value.todos)
        coVerify(exactly = 1) { todos.page(GROUP, "c1") }
    }

    private fun todo(id: Long, title: String, assignee: Long?, createdBy: Long, done: Boolean = false) =
        Todo(id, GROUP, title, assignee, null, done, null, null, createdBy, version = 1)

    private companion object {
        const val GROUP = 1L
        const val ME = 10L
        const val DAD = 11L
    }
}
