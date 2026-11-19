package com.famlog.feature.place

import app.cash.turbine.test
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceAlias
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlacePage
import com.famlog.core.model.PlaceSummary
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

class PlaceAliasViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val details = MutableStateFlow(mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.MEMBER, emptyList())))
    private val groups = mockk<GroupRepository>(relaxed = true) {
        every { currentGroupId } returns MutableStateFlow(GROUP)
        every { details } returns this@PlaceAliasViewModelTest.details
    }
    private val mine = PlaceAlias(1, "우리 집", null, "경기 수원시", ME)
    private val dads = PlaceAlias(2, "할머니 댁", PlaceSummary(9, "해주냉면", "서울", PlaceCategory.RESTAURANT), null, DAD)
    private val naengmyeon = Place(
        id = 9, groupId = GROUP, kakaoPlaceId = "k9", name = "해주냉면", category = PlaceCategory.RESTAURANT,
        categoryDetail = null, address = "서울 송파구", region1 = "서울", region2 = "송파구", region3 = null, lat = 37.5,
        lng = 127.1, phone = null, placeUrl = null, rating = null, oneLineReview = null, sharedBy = DAD,
        marks = PlaceMarks(), createdAt = Instant.parse("2026-11-12T00:00:00Z"),
    )
    private val places = mockk<PlaceRepository> {
        coEvery { aliases(GROUP) } returns ApiResult.Success(listOf(mine, dads))
        coEvery { page(GROUP, PlaceFilter()) } returns ApiResult.Success(PlacePage(listOf(naengmyeon), null))
    }

    private fun viewModel() = PlaceAliasViewModel(places, groups, FakeSessionStore(userId = ME))

    @Test
    fun 내가_만든_별칭만_지울_수_있고_관리자는_모두_지울_수_있다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(listOf(mine, dads), state.aliases)
        assertTrue(state.canDelete(mine))
        assertFalse(state.canDelete(dads))

        details.value = mapOf(GROUP to GroupDetail(GROUP, "우리 가족", GroupRole.ADMIN, emptyList()))
        assertTrue(viewModel.uiState.value.canDelete(dads))
    }

    @Test
    fun 별칭이_비면_저장하지_않고_오류를_보인다() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.openSheet()
        viewModel.onAliasChange("   ")
        viewModel.save()

        assertTrue(viewModel.uiState.value.sheet!!.showErrors)
        coVerify(exactly = 0) { places.createAlias(any(), any(), any(), any()) }
    }

    @Test
    fun 장소를_골라_저장하면_목록을_다시_받는다() = runTest {
        coEvery { places.createAlias(GROUP, "외갓집", 9, "") } returns ApiResult.Success(PlaceAlias(3, "외갓집", null, null, ME))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.openSheet()
        viewModel.onAliasChange("외갓집" + "가".repeat(40))
        assertEquals(30, viewModel.uiState.value.sheet!!.alias.length)
        viewModel.onAliasChange("외갓집")
        viewModel.openPlacePicker()
        viewModel.onPlaceChange(viewModel.uiState.value.placePicker!!.places.single())
        viewModel.save()

        coVerify { places.createAlias(GROUP, "외갓집", 9, "") }
        assertNull(viewModel.uiState.value.sheet)
        coVerify(exactly = 2) { places.aliases(GROUP) }
    }

    @Test
    fun 같은_별칭이_있으면_입력칸에_알린다() = runTest {
        coEvery { places.createAlias(any(), any(), any(), any()) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.ALIAS_DUPLICATED, "이미 있어요", null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.openSheet()
        viewModel.onAliasChange("우리 집")
        viewModel.save()
        assertTrue(viewModel.uiState.value.sheet!!.duplicated)
        assertFalse(viewModel.uiState.value.sheet!!.saving)

        // 고치기 시작하면 오류를 지운다
        viewModel.onAliasChange("우리 집2")
        assertFalse(viewModel.uiState.value.sheet!!.duplicated)
    }

    @Test
    fun 삭제하면_목록에서_빼고_권한이_없어_실패하면_서버_안내() = runTest {
        coEvery { places.deleteAlias(GROUP, mine) } returns ApiResult.Success(Unit)
        coEvery { places.deleteAlias(GROUP, dads) } returns
            ApiResult.Failure(403, ApiError(ErrorCodes.GROUP_ROLE_REQUIRED, "만든 사람이나 관리자만", null))
        val viewModel = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { viewModel.uiState.collect {} }

        viewModel.delete(mine)
        assertEquals(listOf(dads), viewModel.uiState.value.aliases)

        viewModel.events.test {
            viewModel.delete(dads)
            assertEquals(PlaceAliasEvent.Message(UiText.Plain("만든 사람이나 관리자만")), awaitItem())
        }
        assertEquals(listOf(dads), viewModel.uiState.value.aliases)
    }

    private companion object {
        const val GROUP = 1L
        const val ME = 1L
        const val DAD = 2L
    }
}
