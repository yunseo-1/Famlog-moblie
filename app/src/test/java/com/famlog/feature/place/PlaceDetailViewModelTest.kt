package com.famlog.feature.place

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceEdit
import com.famlog.core.model.PlaceMarkKind
import com.famlog.core.model.PlaceMarks
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
import java.time.Instant

class PlaceDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val members = listOf(
        Member(ME, "윤서", GroupRole.MEMBER, null, "막내", null),
        Member(DAD, "김아빠", GroupRole.OWNER, null, "아빠", null),
    )
    private val details = MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, members)))
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { details } returns this@PlaceDetailViewModelTest.details
    }
    private val dadsPlace = Place(
        id = PLACE, groupId = GROUP, kakaoPlaceId = "k1", name = "해주냉면", category = PlaceCategory.RESTAURANT,
        categoryDetail = "냉면", address = "서울 송파구", region1 = "서울", region2 = "송파구", region3 = null,
        lat = 37.5, lng = 127.1, phone = null, placeUrl = null, rating = 5, oneLineReview = "물냉면",
        sharedBy = DAD, marks = PlaceMarks(visited = listOf(DAD)), createdAt = Instant.parse("2026-11-12T00:00:00Z"),
    )
    private val places = mockk<PlaceRepository> {
        coEvery { detail(PLACE) } returns ApiResult.Success(dadsPlace)
    }

    private fun viewModel() = PlaceDetailViewModel(
        SavedStateHandle(mapOf("placeId" to PLACE)), places, groups, FakeSessionStore(userId = ME),
    )

    @Test
    fun 다른_사람이_공유한_장소는_일반_구성원이_고칠_수_없고_관리자는_고칠_수_있다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        assertEquals(dadsPlace, viewModel.uiState.value.place)
        assertFalse(viewModel.uiState.value.canEdit)
        assertEquals(listOf("아빠"), viewModel.uiState.value.markedNames(PlaceMarkKind.VISITED)) // 가족 호칭

        details.value = mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.ADMIN, members))
        assertTrue(viewModel.uiState.value.canEdit)
    }

    @Test
    fun 다녀옴은_바로_보이고_서버_값으로_맞춘다() = runTest {
        val response = CompletableDeferred<ApiResult<PlaceMarks>>()
        coEvery { places.setMark(dadsPlace, PlaceMarkKind.VISITED, true) } coAnswers { response.await() }
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.toggleMark(PlaceMarkKind.VISITED)
        assertTrue(viewModel.uiState.value.marked(PlaceMarkKind.VISITED))
        viewModel.toggleMark(PlaceMarkKind.VISITED) // 응답 전 다시 누르면 무시

        response.complete(ApiResult.Success(PlaceMarks(visited = listOf(DAD, ME), want = listOf(DAD))))
        assertEquals(PlaceMarks(visited = listOf(DAD, ME), want = listOf(DAD)), viewModel.uiState.value.place?.marks)
        coVerify(exactly = 1) { places.setMark(any(), any(), any()) }
    }

    @Test
    fun 표시가_실패하면_되돌리고_알린다() = runTest {
        coEvery { places.setMark(any(), any(), any()) } returns ApiResult.NetworkError
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.events.test {
            viewModel.toggleMark(PlaceMarkKind.WANT)
            assertTrue(awaitItem() is PlaceDetailEvent.Message)
        }
        assertFalse(viewModel.uiState.value.marked(PlaceMarkKind.WANT))
        assertEquals(dadsPlace.marks, viewModel.uiState.value.place?.marks)
    }

    @Test
    fun 수정에서_별점과_한줄평을_비우면_지우라고_보낸다() = runTest {
        val updated = dadsPlace.copy(category = PlaceCategory.TRAVEL, rating = null, oneLineReview = null)
        coEvery { places.edit(dadsPlace, any()) } returns ApiResult.Success(updated)
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.openEdit()
        assertEquals(PlaceEditSheet(PlaceCategory.RESTAURANT, 5, "물냉면"), viewModel.uiState.value.editSheet)
        viewModel.onEditCategory(PlaceCategory.TRAVEL)
        viewModel.onEditRating(5) // 같은 별 → 지움
        viewModel.onEditReview("   ")
        viewModel.saveEdit()

        coVerify { places.edit(dadsPlace, PlaceEdit(PlaceCategory.TRAVEL, rating = null, oneLineReview = null)) }
        assertNull(viewModel.uiState.value.editSheet)
        assertEquals(updated, viewModel.uiState.value.place)
    }

    @Test
    fun 권한이_없어_403이면_시트를_두고_서버_안내를_보인다() = runTest {
        coEvery { places.edit(any(), any()) } returns
            ApiResult.Failure(403, ApiError(ErrorCodes.GROUP_ROLE_REQUIRED, "공유한 사람이나 관리자만", null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        viewModel.openEdit()

        viewModel.events.test {
            viewModel.saveEdit()
            assertEquals(PlaceDetailEvent.Message(UiText.Plain("공유한 사람이나 관리자만")), awaitItem())
        }
        assertEquals(false, viewModel.uiState.value.editSheet?.saving)
    }

    @Test
    fun 삭제하면_닫는다() = runTest {
        coEvery { places.delete(dadsPlace) } returns ApiResult.Success(Unit)
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.events.test {
            viewModel.delete()
            assertEquals(PlaceDetailEvent.Closed, awaitItem())
        }
    }

    @Test
    fun 채팅_카드에서_지워진_장소를_열면_404로_삭제된_장소를_보인다() = runTest {
        coEvery { places.detail(PLACE) } returns ApiResult.Failure(404, ApiError(ErrorCodes.NOT_FOUND, "없어요", null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value.missing)
        assertFalse(viewModel.uiState.value.loadFailed)
        assertNull(viewModel.uiState.value.place)
    }

    @Test
    fun 연결이_끊겨_못_받으면_다시_시도할_수_있다() = runTest {
        coEvery { places.detail(PLACE) } returns ApiResult.NetworkError
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.missing)

        coEvery { places.detail(PLACE) } returns ApiResult.Success(dadsPlace)
        viewModel.retry()
        assertEquals(dadsPlace, viewModel.uiState.value.place)
        assertFalse(viewModel.uiState.value.loadFailed)
    }

    private companion object {
        const val GROUP = 1L
        const val PLACE = 10L
        const val ME = 1L
        const val DAD = 2L
    }
}
