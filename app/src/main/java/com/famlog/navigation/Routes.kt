package com.famlog.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.famlog.R
import com.famlog.feature.group.GroupStartMode
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute
@Serializable data object CalendarRoute
@Serializable data object PlaceRoute
@Serializable data object AlbumRoute
@Serializable data object StoryRoute

/** 앱 시작 시 로그인·그룹 상태를 확인하는 동안 보이는 화면 (AUTH-01) */
@Serializable data object StartRoute
@Serializable data object LoginRoute

/** SCR-02. [canGoBack]이면 그룹 전환 시트에서 온 것이라 뒤로 갈 수 있다 */
@Serializable data class GroupStartRoute(
    val mode: GroupStartMode = GroupStartMode.CHOOSE,
    val canGoBack: Boolean = false,
)

/** SCR-11 그룹 채팅방. 하단 탭 없이 전체 화면 */
@Serializable data class ChatRoomRoute(val groupId: Long)

/** 사진·동영상 전체 화면 (CHAT-05) */
@Serializable data class MediaViewerRoute(val groupId: Long, val messageId: Long, val index: Int)

/** SCR-03 */
@Serializable data class RoleSetupRoute(val groupId: Long)

/**
 * 하단 탭 5개 (사양서 3.1). 채팅 탭만 전체 그룹 목록이라 앱 바 제목이 "FamLog"이고 그룹 전환이 없다.
 * 나머지 탭은 현재 가족 그룹 이름 + ▾. 그룹이 1개여도 ▾를 보인다: 시트에 [가족 초대]·[새 그룹]·[코드 참여]가 있어서
 * (figma-map 2장은 1개면 숨김 — 다른 진입점이 생기면 다시 맞춘다).
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
