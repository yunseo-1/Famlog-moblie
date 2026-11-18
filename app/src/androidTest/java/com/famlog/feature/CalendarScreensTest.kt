package com.famlog.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Holiday
import com.famlog.core.model.Member
import com.famlog.core.model.Occurrence
import com.famlog.core.model.Todo
import com.famlog.feature.calendar.CalendarContent
import com.famlog.feature.calendar.CalendarMode
import com.famlog.feature.calendar.CalendarUiState
import com.famlog.feature.calendar.TodoContent
import com.famlog.feature.calendar.TodoUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CalendarScreensTest {

    @get:Rule val compose = createComposeRule()

    private val dad = Member(1, "김아빠", GroupRole.OWNER, null, "아빠", "#14A3B8")
    private val mom = Member(2, "김엄마", GroupRole.MEMBER, null, "엄마", "#E5568D")

    private fun occurrence(id: Long, title: String, member: Member, day: Int): Occurrence {
        val date = LocalDate.of(2026, 11, day)
        val start = date.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
        return Occurrence(id, title, false, start, start.plusSeconds(3600), date, date, listOf(member.userId), member.userId, false)
    }

    private val baseState = CalendarUiState(
        groupId = 1,
        members = listOf(dad, mom),
        month = YearMonth.of(2026, 11),
        selectedDate = LocalDate.of(2026, 11, 18),
        today = LocalDate.of(2026, 11, 18),
        occurrences = listOf(
            occurrence(1, "엄마 일정 1", mom, 3),
            occurrence(2, "엄마 일정 2", mom, 10),
            occurrence(3, "엄마 일정 3", mom, 17),
            occurrence(4, "아빠 일정 1", dad, 5),
            occurrence(5, "아빠 일정 2", dad, 12),
        ),
    )

    private fun setContent(initial: CalendarUiState) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            FamLogTheme {
                CalendarContent(
                    state = state,
                    onSelectTab = {},
                    onSelectAll = { state = state.copy(selectedMembers = emptySet()) },
                    onToggleMember = { id ->
                        val selected = state.selectedMembers
                        state = state.copy(selectedMembers = if (id in selected) selected - id else selected + id)
                    },
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectMode = { state = state.copy(mode = it) },
                    onSelectDate = { state = state.copy(selectedDate = it) },
                    onRetry = {},
                    onOpenSchedule = {},
                    onAddSchedule = {},
                )
            }
        }
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    /** 인수 기준 SCH-02: 엄마 일정 3개·아빠 일정 2개일 때 "엄마" 필터는 3개, "전체"는 5개를 보여준다 */
    @Test
    fun 엄마_필터는_3개_전체는_5개를_보여준다() {
        setContent(baseState.copy(mode = CalendarMode.LIST))

        assertEquals(3, count("엄마 일정"))
        assertEquals(2, count("아빠 일정"))

        compose.onAllNodesWithText("엄마")[0].performClick()
        compose.waitForIdle()
        assertEquals(3, count("엄마 일정"))
        assertEquals(0, count("아빠 일정"))

        compose.onNodeWithText("전체").performClick()
        compose.waitForIdle()
        assertEquals(3, count("엄마 일정"))
        assertEquals(2, count("아빠 일정"))
    }

    @Test
    fun 공휴일을_고르면_이름을_보이고_날짜_설명에_넣는다() {
        setContent(
            baseState.copy(
                month = YearMonth.of(2026, 12),
                selectedDate = LocalDate.of(2026, 12, 25),
                holidays = mapOf(LocalDate.of(2026, 12, 25) to Holiday(LocalDate.of(2026, 12, 25), "성탄절", false)),
                occurrences = emptyList(),
            ),
        )

        compose.onNodeWithText("성탄절").assertExists()
        compose.onNodeWithText("일정이 없어요.").assertExists()
    }

    @Test
    fun 할_일은_안_한_일과_완료한_일로_나뉘고_체크를_누르면_완료로_바뀐다() {
        val milk = Todo(1, 1, "우유 사 오기", mom.userId, LocalDate.of(2026, 11, 17), false, null, null, mom.userId, 1)
        val parcel = Todo(2, 1, "택배 받기", dad.userId, null, true, null, null, dad.userId, 1)
        compose.setContent {
            var state by remember {
                mutableStateOf(TodoUiState(groupId = 1, members = listOf(dad, mom), todos = listOf(milk, parcel), today = LocalDate.of(2026, 11, 18)))
            }
            FamLogTheme {
                TodoContent(
                    state = state,
                    onSelectAll = {},
                    onToggleMember = {},
                    onToggleDone = { todo ->
                        state = state.copy(todos = state.todos.map { if (it.id == todo.id) it.copy(done = !it.done) else it })
                    },
                    onOpen = {},
                    onAdd = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("할 일 1").assertExists()
        compose.onNodeWithText("완료한 일").assertExists()
        // 기한이 지난 할 일은 "지남"
        compose.onNodeWithText("· 11월 17일 (화)까지 · 지남").assertExists()

        compose.onNodeWithContentDescription("우유 사 오기 완료").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("할 일 0").assertExists()
        compose.onNodeWithContentDescription("우유 사 오기 완료 취소").assertExists()
    }
}
