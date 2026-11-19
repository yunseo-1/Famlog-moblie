package com.famlog.feature.place

import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceAlias
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlacePage
import com.famlog.core.model.RegionCount
import com.famlog.core.model.RegionCounts
import com.famlog.core.model.SubRegionCount
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PlaceListViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-19T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val members = listOf(
        Member(ME, "윤서", GroupRole.MEMBER, null, "막내", null),
        Member(DAD, "김아빠", GroupRole.OWNER, null, "아빠", null),
    )
    private val selectedGroupId = MutableStateFlow<Long?>(GROUP)
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { currentGroupId } returns selectedGroupId
        every { details } returns MutableStateFlow(
            mapOf(
                GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, members),
                OTHER_GROUP to GroupDetail(OTHER_GROUP, "외가", GroupRole.MEMBER, members),
            ),
        )
    }

    // 인수 기준: 아빠가 송파구 맛집 2곳·강남구 1곳을 공유했다
    private val songpa1 = place(1, "해주냉면", region2 = "송파구")
    private val songpa2 = place(2, "석촌 국수", region2 = "송파구")
    private val gangnam = place(3, "강남 초밥", region2 = "강남구")
    private val counts = RegionCounts(
        total = 3,
        items = listOf(RegionCount("서울", 3, listOf(SubRegionCount("강남구", 1), SubRegionCount("송파구", 2)))),
    )
    private val alias = PlaceAlias(1, "할머니 댁", null, "경기 수원시", ME)
    private val changes = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val places = mockk<PlaceRepository> {
        every { changes } returns this@PlaceListViewModelTest.changes
        coEvery { page(any(), any(), any()) } returns ApiResult.Success(PlacePage(listOf(songpa1, songpa2, gangnam), null))
        coEvery { regions(any(), any()) } returns ApiResult.Success(counts)
        coEvery { aliases(any()) } returns ApiResult.Success(listOf(alias))
    }

    private fun viewModel() = PlaceListViewModel(places, groups, clock)

    @Test
    fun 서울_송파구와_아빠를_고르면_그_조건으로_다시_받는다() = runTest {
        val target = PlaceFilter(region1 = "서울", region2 = "송파구", sharedBy = setOf(DAD))
        coEvery { places.page(GROUP, target, null) } returns ApiResult.Success(PlacePage(listOf(songpa1, songpa2), null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        assertEquals(3, viewModel.uiState.value.places.size)

        viewModel.selectRegion("서울", "송파구")
        viewModel.toggleMember(DAD)

        val state = viewModel.uiState.value
        assertEquals(target, state.filter)
        assertEquals(listOf(songpa1, songpa2), state.places)
        coVerify { places.page(GROUP, target, null) }
        // 헤더 개수도 같은 조건의 배지 값
        assertEquals(2L, counts.countOf(state.filter))
    }

    @Test
    fun 지역만_바꾸면_배지는_다시_받지_않고_구성원_카테고리는_다시_받는다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        coVerify(exactly = 1) { places.regions(GROUP, any()) }

        viewModel.selectRegion("서울", null)
        coVerify(exactly = 1) { places.regions(GROUP, any()) }

        viewModel.toggleCategory(PlaceCategory.CAFE)
        viewModel.toggleMember(DAD)
        coVerify { places.regions(GROUP, PlaceFilter(region1 = "서울", sharedBy = setOf(DAD), categories = setOf(PlaceCategory.CAFE))) }

        viewModel.selectAllCategories()
        viewModel.selectAllMembers()
        assertEquals(PlaceFilter(region1 = "서울"), viewModel.uiState.value.filter)
    }

    @Test
    fun 시도를_바꾸면_시군구는_비우고_전체_지역이면_둘_다_비운다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.selectRegion("서울", "송파구")
        viewModel.selectRegion("세종", null)
        assertEquals(PlaceFilter(region1 = "세종"), viewModel.uiState.value.filter)

        viewModel.selectRegion(null, "송파구")
        assertEquals(PlaceFilter(), viewModel.uiState.value.filter)
    }

    @Test
    fun 그룹을_바꾸면_필터를_비우고_그_그룹_목록을_받는다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }
        viewModel.toggleMember(DAD)

        selectedGroupId.value = OTHER_GROUP

        assertEquals(PlaceFilter(), viewModel.uiState.value.filter)
        coVerify { places.page(OTHER_GROUP, PlaceFilter(), null) }
    }

    @Test
    fun 공유_수정_삭제가_있으면_목록과_배지를_다시_받는다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        changes.emit(GROUP)
        changes.emit(OTHER_GROUP) // 다른 그룹은 무시

        coVerify(exactly = 2) { places.page(GROUP, PlaceFilter(), null) }
        coVerify(exactly = 2) { places.regions(GROUP, PlaceFilter()) }
    }

    @Test
    fun 다음_페이지는_커서로_이어_받고_실패하면_안내한다() = runTest {
        coEvery { places.page(GROUP, PlaceFilter(), null) } returns ApiResult.Success(PlacePage(listOf(songpa1), "c2"))
        coEvery { places.page(GROUP, PlaceFilter(), "c2") } returns ApiResult.Success(PlacePage(listOf(songpa2, songpa1), null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.loadMore()
        assertEquals(listOf(songpa1, songpa2), viewModel.uiState.value.places)

        coEvery { places.page(GROUP, PlaceFilter(), null) } returns ApiResult.NetworkError
        viewModel.refresh()
        assertTrue(viewModel.uiState.value.loadFailed)
    }

    @Test
    fun 별칭_수를_받아_리스트_위에_보인다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        assertEquals(1, viewModel.uiState.value.aliasCount)
    }

    @Test
    fun 배지_개수는_고른_지역에_맞춘다() {
        assertEquals(3L, counts.countOf(PlaceFilter()))
        assertEquals(3L, counts.countOf(PlaceFilter(region1 = "서울")))
        assertEquals(1L, counts.countOf(PlaceFilter(region1 = "서울", region2 = "강남구")))
        assertEquals(0L, counts.countOf(PlaceFilter(region1 = "부산")))
    }

    private fun place(id: Long, name: String, region2: String) = Place(
        id = id, groupId = GROUP, kakaoPlaceId = "k$id", name = name, category = PlaceCategory.RESTAURANT,
        categoryDetail = null, address = null, region1 = "서울", region2 = region2, region3 = null, lat = 37.5, lng = 127.1,
        phone = null, placeUrl = null, rating = null, oneLineReview = null, sharedBy = DAD, marks = PlaceMarks(),
        createdAt = Instant.parse("2026-11-12T00:00:00Z"),
    )

    private companion object {
        const val GROUP = 1L
        const val OTHER_GROUP = 2L
        const val ME = 1L
        const val DAD = 2L
    }
}
