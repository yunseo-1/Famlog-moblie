package com.famlog.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.Coordinates
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.MessageType
import com.famlog.core.model.NearbyPlace
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceAlias
import com.famlog.core.model.PlaceCard
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlaceSummary
import com.famlog.core.model.RegionCount
import com.famlog.core.model.RegionCounts
import com.famlog.core.model.SendState
import com.famlog.core.model.SubRegionCount
import com.famlog.feature.chat.ChatBubble
import com.famlog.feature.place.ConsentPrompt
import com.famlog.feature.place.LocationConsentDialog
import com.famlog.feature.place.PlaceAliasContent
import com.famlog.feature.place.PlaceAliasUiState
import com.famlog.feature.place.PlaceDetailContent
import com.famlog.feature.place.PlaceDetailUiState
import com.famlog.feature.place.PlaceListContent
import com.famlog.feature.place.PlaceListUiState
import com.famlog.feature.place.PlaceMapContent
import com.famlog.feature.place.PlaceMapUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlaceScreensTest {

    @get:Rule val compose = createComposeRule()

    private val dad = Member(2, "김아빠", GroupRole.OWNER, null, "아빠", "#14A3B8")
    private val me = Member(1, "김윤서", GroupRole.MEMBER, null, "막내", "#E5568D")

    private fun place(id: Long, name: String, region2: String) = Place(
        id = id, groupId = 1, kakaoPlaceId = "k$id", name = name, category = PlaceCategory.RESTAURANT,
        categoryDetail = "냉면", address = "서울 $region2", region1 = "서울", region2 = region2, region3 = null,
        lat = 37.5, lng = 127.1, phone = null, placeUrl = null, rating = 5, oneLineReview = null,
        sharedBy = dad.userId, marks = PlaceMarks(), createdAt = Instant.parse("2026-11-12T03:00:00Z"),
    )

    private val listState = PlaceListUiState(
        groupId = 1,
        members = listOf(me, dad),
        places = listOf(place(1, "해주냉면", "송파구"), place(3, "강남 초밥", "강남구")),
        regions = RegionCounts(3, listOf(RegionCount("서울", 3, listOf(SubRegionCount("강남구", 1), SubRegionCount("송파구", 2))))),
        today = LocalDate.of(2026, 11, 19),
    )

    @Test
    fun 목록은_공유한_사람과_날짜를_보이고_시도를_펼쳐_시군구를_고른다() {
        var region: Pair<String?, String?>? = null
        var toggled: Long? = null
        compose.setContent {
            FamLogTheme {
                PlaceListContent(
                    state = listState,
                    onSelectRegion = { r1, r2 -> region = r1 to r2 },
                    onSelectAllMembers = {},
                    onToggleMember = { toggled = it },
                    onSelectAllCategories = {},
                    onToggleCategory = {},
                    onLoadMore = {},
                    onRetry = {},
                    onOpenPlace = {},
                )
            }
        }

        compose.onNodeWithText("전체 지역 · 3곳").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("맛집 · 아빠 공유 · 11/12").fetchSemanticsNodes().size)
        compose.onNodeWithText("해주냉면").assertIsDisplayed()

        compose.onNodeWithText("아빠").performClick()
        assertEquals(dad.userId, toggled)

        compose.onNodeWithContentDescription("지역 선택").performClick()
        compose.onNodeWithText("서울").performClick()
        compose.onNodeWithText("송파구").performClick()
        assertEquals("서울" to "송파구", region)
    }

    @Test
    fun 지워진_장소는_삭제된_장소로_보인다() {
        compose.setContent {
            FamLogTheme {
                PlaceDetailContent(
                    state = PlaceDetailUiState(loading = false, missing = true),
                    onBack = {}, onRetry = {}, onToggleMark = {}, onEdit = {}, onDelete = {}, onCall = {}, onOpenUrl = {},
                )
            }
        }

        compose.onNodeWithText("삭제된 장소예요.").assertIsDisplayed()
        compose.onNodeWithText("수정").assertDoesNotExist()
    }

    @Test
    fun 채팅_장소_카드는_이름과_분류_지역_별점을_보이고_누르면_장소를_연다() {
        var opened: Long? = null
        val message = ChatMessage(
            key = "id:21", groupId = 1, id = 21, seq = 23, senderId = dad.userId, clientMessageId = null,
            type = MessageType.PLACE, body = "해주냉면", replyTo = null, reactions = emptyList(), unreadCount = 0,
            deleted = false, sentAt = Instant.parse("2026-11-19T03:00:00Z"), sendState = SendState.SENT,
            place = PlaceCard(1, "해주냉면", PlaceCategory.RESTAURANT, "서울 송파구", "서울", "송파구", 5),
        )
        compose.setContent {
            FamLogTheme {
                ChatBubble(
                    message = message, mine = false, senderName = "아빠", replyName = null, showSender = true, showTime = true,
                    myUserId = me.userId, onLongPress = {}, onReactionClick = {}, onOpenPlace = { opened = it },
                )
            }
        }

        compose.onNodeWithText("맛집 · 서울 송파구").assertIsDisplayed()
        compose.onNodeWithContentDescription("별점 5점").assertIsDisplayed()
        compose.onNodeWithText("해주냉면").performClick()
        assertEquals(1L, opened)
    }

    @Test
    fun 지도_탭은_내_주변_결과를_거리와_함께_보이고_닫을_수_있으며_기능이_꺼지면_버튼을_숨긴다() {
        var closed = false
        var state by mutableStateOf(
            PlaceMapUiState(
                groupId = 1,
                members = listOf(me, dad),
                initialCenter = Coordinates(37.5, 127.1),
                nearby = listOf(NearbyPlace(place(1, "해주냉면", "송파구"), 1200)),
                today = LocalDate.of(2026, 11, 19),
            ),
        )
        compose.setContent {
            FamLogTheme {
                PlaceMapContent(
                    state = state,
                    onSelectAllCategories = {}, onToggleCategory = {}, onNearby = {}, onCloseNearby = { closed = true },
                    onRetry = {}, onOpenPlace = {},
                    map = { _, modifier -> Box(modifier) },
                )
            }
        }

        compose.onNodeWithText("내 주변 3km · 1곳").assertIsDisplayed()
        compose.onNodeWithText("1.2km").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        assertTrue(closed)

        compose.onNodeWithText("내 주변").assertIsDisplayed()
        state = state.copy(nearby = null, nearbyAvailable = false)
        compose.onNodeWithText("내 주변").assertDoesNotExist()
        compose.onNodeWithText("화면 안 0곳").assertIsDisplayed()
    }

    @Test
    fun 위치_약관은_버전을_보이고_동의를_누르면_알린다() {
        var agreed = false
        compose.setContent {
            FamLogTheme { LocationConsentDialog(ConsentPrompt("1.0"), onAgree = { agreed = true }, onDismiss = {}) }
        }

        compose.onNodeWithText("위치기반서비스 이용 동의").assertIsDisplayed()
        compose.onNodeWithText("위치기반서비스 이용약관 1.0", substring = true).assertIsDisplayed()
        compose.onNodeWithText("동의하고 계속").performClick()
        assertTrue(agreed)
    }

    @Test
    fun 별칭은_연결한_장소와_주소를_보이고_내가_만든_것만_지울_수_있다() {
        var deleted: PlaceAlias? = null
        val mine = PlaceAlias(1, "우리 집", null, "경기 수원시 영통구", me.userId)
        val dads = PlaceAlias(2, "할머니 댁", PlaceSummary(9, "해주냉면", "서울 송파구", PlaceCategory.RESTAURANT), null, dad.userId)
        compose.setContent {
            FamLogTheme {
                PlaceAliasContent(
                    state = PlaceAliasUiState(groupId = 1, myUserId = me.userId, aliases = listOf(mine, dads), loading = false),
                    onBack = {}, onAdd = {}, onDelete = { deleted = it }, onOpenPlace = {}, onRetry = {},
                )
            }
        }

        compose.onNodeWithText("경기 수원시 영통구").assertIsDisplayed()
        compose.onNodeWithText("해주냉면 · 서울 송파구").assertIsDisplayed()
        compose.onNodeWithContentDescription("할머니 댁 삭제").assertDoesNotExist()
        compose.onNodeWithContentDescription("우리 집 삭제").performClick()
        assertEquals(mine, deleted)
    }
}
