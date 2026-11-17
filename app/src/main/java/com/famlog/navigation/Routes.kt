package com.famlog.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.famlog.R
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute
@Serializable data object CalendarRoute
@Serializable data object PlaceRoute
@Serializable data object AlbumRoute
@Serializable data object StoryRoute
@Serializable data object LoginRoute

/**
 * 하단 탭 5개 (사양서 3.1). 채팅 탭만 전체 그룹 목록이라 앱 바 제목이 "FamLog"이고 그룹 전환이 없다.
 * 나머지 탭은 현재 가족 그룹 이름 + ▾ (figma-map 2장).
 */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val showsGroupSwitcher: Boolean,
) {
    CHAT(ChatRoute, R.string.tab_chat, R.drawable.ic_tab_chat, showsGroupSwitcher = false),
    CALENDAR(CalendarRoute, R.string.tab_calendar, R.drawable.ic_tab_calendar, showsGroupSwitcher = true),
    PLACE(PlaceRoute, R.string.tab_place, R.drawable.ic_tab_place, showsGroupSwitcher = true),
    ALBUM(AlbumRoute, R.string.tab_album, R.drawable.ic_tab_album, showsGroupSwitcher = true),
    STORY(StoryRoute, R.string.tab_story, R.drawable.ic_tab_story, showsGroupSwitcher = true),
}
