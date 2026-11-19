package com.famlog.feature.place

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlaceSearchItem
import com.famlog.core.model.PlaceSearchPage
import com.famlog.core.model.PlaceShare
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PlaceSearchViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { currentGroupId } returns MutableStateFlow(GROUP)
    }
    private val naengmyeon = item("k1", "해주냉면", PlaceCategory.RESTAURANT)
    private val shared = item("k2", "이미 공유한 곳", PlaceCategory.CAFE, sharedPlaceId = 7)
    private val places = mockk<PlaceRepository> {
        coEvery { search("냉면", 1, GROUP) } returns ApiResult.Success(PlaceSearchPage(listOf(naengmyeon, shared), hasMore = true))
        coEvery { search("냉면", 2, GROUP) } returns ApiResult.Success(PlaceSearchPage(listOf(item("k3", "옥천냉면")), hasMore = false))
    }

    private fun viewModel() = PlaceSearchViewModel(places, groups)

    @Test
    fun 입력을_멈추면_잠시_뒤_한_번만_검색한다() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.onQueryChange("냉")
        advanceTimeBy(200)
        viewModel.onQueryChange("냉면")
        advanceTimeBy(399)
        coVerify(exactly = 0) { places.search(any(), any(), any()) }

        advanceTimeBy(2)
        coVerify(exactly = 1) { places.search("냉면", 1, GROUP) }
        coVerify(exactly = 0) { places.search("냉", any(), any()) }
        assertEquals(listOf("k1", "k2"), viewModel.uiState.value.results.map { it.kakaoPlaceId })
    }

    @Test
    fun 키보드_검색은_바로_하고_끝에_가면_다음_쪽을_붙인다() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("냉면")
        viewModel.searchNow()
        coVerify(exactly = 1) { places.search("냉면", 1, GROUP) }

        viewModel.loadMore()
        val state = viewModel.uiState.value
        assertEquals(listOf("k1", "k2", "k3"), state.results.map { it.kakaoPlaceId })
        assertEquals(2, state.page)
        assertEquals(false, state.hasMore)

        // 다 받았으면 더 부르지 않는다
        viewModel.loadMore()
        coVerify(exactly = 0) { places.search("냉면", 3, any()) }
    }

    @Test
    fun 검색어를_지우면_결과도_비운다() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("냉면")
        viewModel.searchNow()

        viewModel.onQueryChange("")

        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals("", viewModel.uiState.value.searchedQuery)
    }

    @Test
    fun 너무_자주_검색하면_서버_안내를_보이고_실패로_표시한다() = runTest(mainRule.dispatcher) {
        coEvery { places.search("냉면", 1, GROUP) } returns
            ApiResult.Failure(429, ApiError(ErrorCodes.RATE_LIMITED, "검색을 너무 자주 하고 있어요.", null))
        val viewModel = viewModel()
        viewModel.onQueryChange("냉면")

        viewModel.events.test {
            viewModel.searchNow()
            assertEquals(PlaceSearchEvent.Message(UiText.Plain("검색을 너무 자주 하고 있어요.")), awaitItem())
        }
        assertTrue(viewModel.uiState.value.searchFailed)
    }

    @Test
    fun 이미_공유한_장소를_고르면_상세로_가고_새_장소는_추천_카테고리로_시트를_연다() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.select(shared)
            assertEquals(PlaceSearchEvent.OpenPlace(7), awaitItem())
        }
        assertNull(viewModel.uiState.value.sheet)

        viewModel.select(naengmyeon)
        val sheet = viewModel.uiState.value.sheet!!
        assertEquals(PlaceCategory.RESTAURANT, sheet.category)
        assertEquals(true, sheet.shareToChat)
        assertNull(sheet.rating)
    }

    @Test
    fun 공유하면_고른_값을_보내고_상세로_간다() = runTest(mainRule.dispatcher) {
        coEvery { places.share(GROUP, any()) } returns ApiResult.Success(place(id = 11))
        val viewModel = viewModel()
        viewModel.select(naengmyeon)
        viewModel.onCategoryChange(PlaceCategory.TRAVEL)
        viewModel.onRatingChange(4)
        viewModel.onRatingChange(4) // 같은 별을 다시 누르면 지운다
        viewModel.onRatingChange(5)
        viewModel.onReviewChange("물냉면 " + "맛".repeat(200))
        viewModel.onShareToChatChange(false)

        viewModel.events.test {
            viewModel.share()
            assertEquals(PlaceSearchEvent.OpenPlace(11), awaitItem())
        }
        coVerify {
            places.share(GROUP, PlaceShare("k1", PlaceCategory.TRAVEL, 5, "물냉면 " + "맛".repeat(96), shareToChat = false))
        }
        assertNull(viewModel.uiState.value.sheet)
    }

    @Test
    fun 그새_다른_가족이_공유했으면_안내하고_다시_검색한다() = runTest(mainRule.dispatcher) {
        coEvery { places.share(GROUP, any()) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.PLACE_ALREADY_SHARED, "이미", null))
        val viewModel = viewModel()
        viewModel.onQueryChange("냉면")
        viewModel.searchNow()
        viewModel.select(naengmyeon)

        viewModel.events.test {
            viewModel.share()
            assertEquals(PlaceSearchEvent.Message(UiText.Res(R.string.place_already_shared)), awaitItem())
        }
        assertNull(viewModel.uiState.value.sheet)
        coVerify(exactly = 2) { places.search("냉면", 1, GROUP) }
    }

    @Test
    fun 검색한_지_오래돼_400이면_서버_안내를_보이고_다시_검색한다() = runTest(mainRule.dispatcher) {
        coEvery { places.share(GROUP, any()) } returns
            ApiResult.Failure(400, ApiError("BAD_REQUEST", "검색한 지 오래된 장소예요. 다시 검색해서 골라 주세요.", null))
        val viewModel = viewModel()
        viewModel.onQueryChange("냉면")
        viewModel.searchNow()
        viewModel.select(naengmyeon)

        viewModel.events.test {
            viewModel.share()
            assertEquals(PlaceSearchEvent.Message(UiText.Plain("검색한 지 오래된 장소예요. 다시 검색해서 골라 주세요.")), awaitItem())
        }
        coVerify(exactly = 2) { places.search("냉면", 1, GROUP) }
    }

    @Test
    fun 연결이_끊겨_실패하면_시트를_두고_다시_누를_수_있다() = runTest(mainRule.dispatcher) {
        coEvery { places.share(GROUP, any()) } returns ApiResult.NetworkError
        val viewModel = viewModel()
        viewModel.select(naengmyeon)

        viewModel.events.test {
            viewModel.share()
            assertEquals(PlaceSearchEvent.Message(UiText.Res(R.string.error_network)), awaitItem())
        }
        assertEquals(false, viewModel.uiState.value.sheet?.saving)
    }

    private fun item(id: String, name: String, category: PlaceCategory = PlaceCategory.RESTAURANT, sharedPlaceId: Long? = null) =
        PlaceSearchItem(id, name, null, category, "서울 송파구", "서울", "송파구", null, sharedPlaceId)

    private fun place(id: Long) = Place(
        id = id, groupId = GROUP, kakaoPlaceId = "k1", name = "해주냉면", category = PlaceCategory.TRAVEL,
        categoryDetail = null, address = null, region1 = "서울", region2 = "송파구", region3 = null, lat = 37.5, lng = 127.1,
        phone = null, placeUrl = null, rating = 5, oneLineReview = null, sharedBy = 1, marks = PlaceMarks(),
        createdAt = Instant.parse("2026-11-19T00:00:00Z"),
    )

    private companion object {
        const val GROUP = 1L
    }
}
