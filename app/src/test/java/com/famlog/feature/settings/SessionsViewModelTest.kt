package com.famlog.feature.settings

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.SessionRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.LoginSession
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** 로그인 기기 관리 (AUTH-03) */
class SessionsViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = mockk<SessionRepository>()

    private fun session(id: Long, current: Boolean = false) =
        LoginSession(id, "기기$id", "ANDROID", Instant.parse("2026-11-01T01:00:00Z"), Instant.parse("2026-11-23T00:00:00Z"), current)

    private val list = listOf(session(9, current = true), session(7), session(3))

    @Test
    fun 들어오면_목록을_받고_실패하면_다시_시도할_수_있다() = runTest(mainRule.dispatcher) {
        coEvery { repository.sessions() } returnsMany listOf(ApiResult.NetworkError, ApiResult.Success(list))

        val viewModel = SessionsViewModel(repository)
        assertTrue(viewModel.state.value.loadFailed)

        viewModel.load()
        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(listOf(9L, 7L, 3L), viewModel.state.value.sessions.map { it.id })
    }

    @Test
    fun 이_기기는_여기서_로그아웃할_수_없다() = runTest(mainRule.dispatcher) {
        coEvery { repository.sessions() } returns ApiResult.Success(list)
        val viewModel = SessionsViewModel(repository)

        viewModel.ask(list[0])

        assertNull(viewModel.state.value.confirm)
    }

    @Test
    fun 다른_기기는_확인한_뒤에_로그아웃시키고_목록에서_뺀다() = runTest(mainRule.dispatcher) {
        coEvery { repository.sessions() } returns ApiResult.Success(list)
        coEvery { repository.revoke(7) } returns ApiResult.Success(Unit)
        val viewModel = SessionsViewModel(repository)

        viewModel.ask(list[1])
        viewModel.cancel()
        assertNull(viewModel.state.value.confirm)
        coVerify(exactly = 0) { repository.revoke(any()) }

        viewModel.events.test {
            viewModel.ask(list[1])
            viewModel.revoke()
            assertEquals(UiText.Res(R.string.sessions_revoked), awaitItem())
        }
        assertEquals(listOf(9L, 3L), viewModel.state.value.sessions.map { it.id })
        assertNull(viewModel.state.value.confirm)
        assertFalse(viewModel.state.value.revoking)
    }

    @Test
    fun 이미_없는_세션이면_목록을_새로_받는다() = runTest(mainRule.dispatcher) {
        coEvery { repository.sessions() } returnsMany listOf(ApiResult.Success(list), ApiResult.Success(list - list[2]))
        coEvery { repository.revoke(3) } returns ApiResult.Failure(404, ApiError("NOT_FOUND", "찾을 수 없어요.", null))
        val viewModel = SessionsViewModel(repository)

        viewModel.ask(list[2])
        viewModel.revoke()

        coVerify(exactly = 2) { repository.sessions() }
        assertEquals(listOf(9L, 7L), viewModel.state.value.sessions.map { it.id })
    }

    @Test
    fun 그_밖의_실패는_서버_문구를_보여주고_목록은_그대로() = runTest(mainRule.dispatcher) {
        coEvery { repository.sessions() } returns ApiResult.Success(list)
        coEvery { repository.revoke(7) } returns ApiResult.NetworkError
        val viewModel = SessionsViewModel(repository)

        viewModel.events.test {
            viewModel.ask(list[1])
            viewModel.revoke()
            assertEquals(UiText.Res(R.string.error_network), awaitItem())
        }
        assertEquals(3, viewModel.state.value.sessions.size)
    }

    @Test
    fun 마지막_접속은_방금_분_시간_그_이상은_날짜() {
        val now = Instant.parse("2026-11-23T03:00:00Z")

        assertEquals(UiText.Res(R.string.sessions_last_now), lastUsedText(now.minusSeconds(30), now))
        assertEquals(UiText.Res(R.string.sessions_last_minutes, listOf(5)), lastUsedText(now.minusSeconds(5 * 60), now))
        assertEquals(UiText.Res(R.string.sessions_last_hours, listOf(3)), lastUsedText(now.minusSeconds(3 * 3600 + 59), now))
        assertEquals(UiText.Plain("2026년 11월 21일"), lastUsedText(Instant.parse("2026-11-21T03:00:00Z"), now))
        assertEquals(UiText.Res(R.string.sessions_last_unknown), lastUsedText(null, now))
    }
}
