package com.famlog.feature.place

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.ConsentRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.LocationSource
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Consent
import com.famlog.core.model.Coordinates
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.MapBounds
import com.famlog.core.model.NearbyPlace
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlacePage
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PlaceMapViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-19T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { currentGroupId } returns MutableStateFlow(GROUP)
        every { details } returns MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, emptyList())))
    }
    private val naengmyeon = place(1, "해주냉면", PlaceCategory.RESTAURANT, 37.50, 127.10)
    private val cafe = place(2, "카페 온화", PlaceCategory.CAFE, 37.51, 127.11)
    private val seoul = MapBounds(37.4, 127.0, 37.6, 127.2)
    private val here = Coordinates(37.505, 127.105)

    private val places = mockk<PlaceRepository> {
        every { changes } returns MutableSharedFlow()
        coEvery { page(GROUP, PlaceFilter()) } returns ApiResult.Success(PlacePage(listOf(cafe, naengmyeon), null))
        coEvery { page(GROUP, PlaceFilter(bounds = seoul)) } returns ApiResult.Success(PlacePage(listOf(cafe, naengmyeon), null))
        coEvery { nearby(GROUP, here) } returns ApiResult.Success(listOf(NearbyPlace(naengmyeon, 120), NearbyPlace(cafe, 900)))
    }
    private val agreed = Consent(Consent.LOCATION, agreed = true, docVersion = "1.0", currentVersion = "1.0", reconsentRequired = false)
    private val notAgreed = agreed.copy(agreed = false, docVersion = null)
    private val consents = mockk<ConsentRepository> {
        coEvery { location() } returns ApiResult.Success(agreed)
    }
    private val location = mockk<LocationSource> {
        coEvery { current() } returns here
    }

    private fun viewModel() = PlaceMapViewModel(places, consents, location, groups, clock)

    /** uiState는 구독할 때만 값이 흐른다 (WhileSubscribed) */
    private fun TestScope.started(): PlaceMapViewModel = viewModel().also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun 가장_최근_공유한_장소에서_시작하고_없으면_서울시청() = runTest(mainRule.dispatcher) {
        assertEquals(Coordinates(cafe.lat, cafe.lng), started().uiState.value.initialCenter)

        coEvery { places.page(GROUP, PlaceFilter()) } returns ApiResult.Success(PlacePage(emptyList(), null))
        assertEquals(PlaceMapViewModel.DEFAULT_CENTER, started().uiState.value.initialCenter)
    }

    @Test
    fun 지도_이동이_멈추면_잠시_뒤_그_영역만_받는다() = runTest(mainRule.dispatcher) {
        val viewModel = started()

        viewModel.onViewportChanged(MapBounds(1.0, 1.0, 2.0, 2.0))
        advanceTimeBy(100)
        viewModel.onViewportChanged(seoul)
        advanceTimeBy(PlaceMapViewModel.VIEWPORT_DEBOUNCE_MS + 1)

        coVerify(exactly = 0) { places.page(GROUP, PlaceFilter(bounds = MapBounds(1.0, 1.0, 2.0, 2.0))) }
        coVerify(exactly = 1) { places.page(GROUP, PlaceFilter(bounds = seoul)) }
        assertEquals(listOf(cafe, naengmyeon), viewModel.uiState.value.pins)
    }

    @Test
    fun 카테고리를_고르면_같은_영역에서_다시_받는다() = runTest(mainRule.dispatcher) {
        val cafes = PlaceFilter(categories = setOf(PlaceCategory.CAFE), bounds = seoul)
        coEvery { places.page(GROUP, cafes) } returns ApiResult.Success(PlacePage(listOf(cafe), null))
        val viewModel = started()
        viewModel.onViewportChanged(seoul)
        advanceTimeBy(PlaceMapViewModel.VIEWPORT_DEBOUNCE_MS + 1)

        viewModel.toggleCategory(PlaceCategory.CAFE)

        assertEquals(listOf(cafe), viewModel.uiState.value.places)
    }

    @Test
    fun 동의했으면_권한을_묻고_허락하면_내_주변을_가까운_순으로_보이고_카메라를_옮긴다() = runTest(mainRule.dispatcher) {
        val viewModel = started()

        viewModel.events.test {
            viewModel.startNearby()
            assertEquals(PlaceMapEvent.RequestLocationPermission, awaitItem())
            viewModel.onLocationPermission(granted = true)
            assertEquals(PlaceMapEvent.MoveCamera(here), awaitItem())
        }
        val state = viewModel.uiState.value
        assertEquals(listOf(120, 900), state.nearbyVisible?.map { it.distanceM })
        assertEquals(listOf(naengmyeon, cafe), state.pins)
        assertEquals(here, state.myLocation)

        // 카테고리 칩은 내 주변 결과에도 적용된다
        viewModel.toggleCategory(PlaceCategory.CAFE)
        assertEquals(listOf(cafe), viewModel.uiState.value.pins)

        viewModel.closeNearby()
        assertNull(viewModel.uiState.value.nearby)
        assertNull(viewModel.uiState.value.myLocation)
    }

    @Test
    fun 동의하지_않았으면_서버가_준_버전으로_약관을_띄우고_동의하면_권한으로() = runTest(mainRule.dispatcher) {
        coEvery { consents.location() } returns ApiResult.Success(notAgreed)
        coEvery { consents.agreeLocation("1.0") } returns ApiResult.Success(agreed)
        val viewModel = started()

        viewModel.startNearby()
        assertEquals(ConsentPrompt("1.0"), viewModel.uiState.value.consentPrompt)

        viewModel.events.test {
            viewModel.agreeLocation()
            assertEquals(PlaceMapEvent.RequestLocationPermission, awaitItem())
        }
        coVerify { consents.agreeLocation("1.0") }
        assertNull(viewModel.uiState.value.consentPrompt)
    }

    @Test
    fun 그새_약관이_바뀌어_400이면_새_버전으로_다시_묻는다() = runTest(mainRule.dispatcher) {
        coEvery { consents.location() } returnsMany listOf(
            ApiResult.Success(notAgreed),
            ApiResult.Success(notAgreed.copy(currentVersion = "1.1")),
        )
        coEvery { consents.agreeLocation("1.0") } returns ApiResult.Failure(400, ApiError("VALIDATION_FAILED", "약관이 바뀌었어요", null))
        val viewModel = started()
        viewModel.startNearby()

        viewModel.events.test {
            viewModel.agreeLocation()
            assertEquals(PlaceMapEvent.Message(UiText.Res(R.string.place_consent_changed)), awaitItem())
        }
        assertEquals(ConsentPrompt("1.1"), viewModel.uiState.value.consentPrompt)
    }

    @Test
    fun 권한을_거절하면_위치를_묻지_않고_안내만_한다() = runTest(mainRule.dispatcher) {
        val viewModel = started()

        viewModel.events.test {
            viewModel.onLocationPermission(granted = false)
            assertEquals(PlaceMapEvent.Message(UiText.Res(R.string.place_location_denied)), awaitItem())
        }
        coVerify(exactly = 0) { location.current() }
        coVerify(exactly = 0) { places.nearby(any(), any(), any()) }
    }

    @Test
    fun 위치를_못_받으면_서버에_묻지_않는다() = runTest(mainRule.dispatcher) {
        coEvery { location.current() } returns null
        val viewModel = started()

        viewModel.events.test {
            viewModel.onLocationPermission(granted = true)
            assertEquals(PlaceMapEvent.Message(UiText.Res(R.string.place_location_failed)), awaitItem())
        }
        coVerify(exactly = 0) { places.nearby(any(), any(), any()) }
        assertFalse(viewModel.uiState.value.nearbyLoading)
    }

    @Test
    fun 기능이_꺼져_404면_내_주변_버튼을_숨긴다() = runTest(mainRule.dispatcher) {
        coEvery { places.nearby(GROUP, here) } returns ApiResult.Failure(404, ApiError(ErrorCodes.NOT_FOUND, "없음", null))
        val viewModel = started()

        viewModel.events.test {
            viewModel.onLocationPermission(granted = true)
            assertEquals(PlaceMapEvent.Message(UiText.Res(R.string.place_nearby_unavailable)), awaitItem())
        }
        assertFalse(viewModel.uiState.value.nearbyAvailable)
        assertNull(viewModel.uiState.value.myLocation)
    }

    @Test
    fun 서버가_동의_없음_403이면_약관을_다시_띄운다() = runTest(mainRule.dispatcher) {
        coEvery { places.nearby(GROUP, here) } returns
            ApiResult.Failure(403, ApiError(ErrorCodes.LOCATION_CONSENT_REQUIRED, "동의 필요", null))
        coEvery { consents.location() } returns ApiResult.Success(notAgreed)
        val viewModel = started()

        viewModel.onLocationPermission(granted = true)

        assertEquals(ConsentPrompt("1.0"), viewModel.uiState.value.consentPrompt)
        assertNull(viewModel.uiState.value.myLocation)
    }

    private fun place(id: Long, name: String, category: PlaceCategory, lat: Double, lng: Double) = Place(
        id = id, groupId = GROUP, kakaoPlaceId = "k$id", name = name, category = category, categoryDetail = null,
        address = null, region1 = "서울", region2 = "송파구", region3 = null, lat = lat, lng = lng, phone = null,
        placeUrl = null, rating = null, oneLineReview = null, sharedBy = 1, marks = PlaceMarks(),
        createdAt = Instant.parse("2026-11-12T00:00:00Z"),
    )

    private companion object {
        const val GROUP = 1L
    }
}
