package com.famlog.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.famlog.R
import com.famlog.core.data.StartDestination
import com.famlog.core.model.ItemSource
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamAppBar
import com.famlog.core.designsystem.component.FamBottomNav
import com.famlog.core.designsystem.component.FamBottomNavItem
import com.famlog.core.network.SessionEvent
import com.famlog.feature.album.AlbumScreen
import com.famlog.feature.auth.LoginScreen
import com.famlog.feature.calendar.CalendarScreen
import com.famlog.feature.calendar.ScheduleDetailScreen
import com.famlog.feature.calendar.ScheduleEditScreen
import com.famlog.feature.chat.CandidateInboxScreen
import com.famlog.feature.chat.ChatFocus
import com.famlog.feature.chat.ChatRoomScreen
import com.famlog.feature.chat.ChatScreen
import com.famlog.feature.chat.MediaViewerScreen
import com.famlog.feature.group.GroupStartMode
import com.famlog.feature.group.GroupStartScreen
import com.famlog.feature.group.GroupSwitcherSheet
import com.famlog.feature.group.RoleSetupScreen
import com.famlog.feature.group.shareInvite
import com.famlog.feature.place.PlaceAliasScreen
import com.famlog.feature.place.PlaceDetailScreen
import com.famlog.feature.place.PlaceScreen
import com.famlog.feature.place.PlaceSearchScreen
import com.famlog.feature.story.StoryScreen

@Composable
fun FamlogNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSwitcher by rememberSaveable { mutableStateOf(false) }
    val switcherState by viewModel.switcherState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, navController) {
        viewModel.sessionEvents.collect { event ->
            when (event) {
                SessionEvent.LoggedOut -> {
                    showSwitcher = false
                    viewModel.consumePendingOpen()
                    navController.navigateTo(StartDestination.Login)
                }
            }
        }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.events.collect { event ->
            when (event) {
                is MainEvent.Navigate -> navController.navigateTo(event.destination)
                is MainEvent.ShareInvite -> context.shareInvite(event.groupName, event.code)
                is MainEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                MainEvent.CloseSwitcher -> showSwitcher = false
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination?.topLevelDestination()

    // 알림을 눌렀으면 시작 분기(로그인·그룹 확인)를 마친 뒤 그 화면을 연다. 채팅방 바닥은 채팅 탭, 일정 상세 바닥은 캘린더 탭
    val openTarget by viewModel.openTarget.collectAsStateWithLifecycle()
    val inMain = backStackEntry?.destination?.isMainArea() == true
    LaunchedEffect(openTarget, inMain) {
        val target = openTarget ?: return@LaunchedEffect
        if (!inMain) return@LaunchedEffect
        viewModel.consumePendingOpen()
        when (target) {
            is OpenTarget.Chat -> navController.navigate(ChatRoomRoute(target.groupId)) { popUpTo(ChatRoute) }
            is OpenTarget.Schedule -> {
                navController.navigateToTab(TopLevelDestination.CALENDAR)
                navController.navigate(ScheduleDetailRoute(target.scheduleId))
            }
        }
    }

    // 프로세스가 다시 만들어져 탭 화면부터 복원되면 시작 분기를 거치지 않으므로 그룹 목록을 다시 받는다
    LaunchedEffect(currentTab != null) {
        if (currentTab != null && switcherState.groups.isEmpty()) viewModel.refreshGroups()
    }

    MainScaffold(
        currentTab = currentTab,
        groupTitle = switcherState.currentGroup?.name.orEmpty(),
        onTabSelected = navController::navigateToTab,
        onGroupSwitcherClick = {
            viewModel.refreshGroups()
            showSwitcher = true
        },
        snackbarHostState = snackbarHostState,
    ) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = StartRoute,
            modifier = contentModifier,
        ) {
            composable<StartRoute> { StartScreen(onNavigate = navController::navigateTo) }
            composable<LoginRoute> { LoginScreen(onNavigate = navController::navigateTo) }
            composable<GroupStartRoute> { entry ->
                val route = entry.toRoute<GroupStartRoute>()
                GroupStartScreen(
                    initialMode = route.mode,
                    onNavigate = navController::navigateTo,
                    onBack = if (route.canGoBack) ({ navController.popBackStack() }) else null,
                )
            }
            composable<RoleSetupRoute> {
                RoleSetupScreen(onDone = { navController.navigateTo(StartDestination.Main) })
            }
            composable<ChatRoute> {
                ChatScreen(
                    onOpenRoom = { navController.navigate(ChatRoomRoute(it)) },
                    onNewGroup = { navController.navigate(GroupStartRoute(canGoBack = true)) },
                )
            }
            composable<ChatRoomRoute> { entry ->
                val route = entry.toRoute<ChatRoomRoute>()
                // 원문 보기로 들어온 위치는 한 번만, 제안함에서 고른 후보는 돌아올 때마다
                var routeFocusDone by rememberSaveable { mutableStateOf(false) }
                val picked by entry.savedStateHandle.getStateFlow<Long?>(FOCUS_CANDIDATE_KEY, null).collectAsStateWithLifecycle()
                val focus = when {
                    picked != null -> ChatFocus(candidateId = picked)
                    !routeFocusDone && (route.focusSeq != null || route.focusCandidateId != null) ->
                        ChatFocus(route.focusSeq, route.focusCandidateId)
                    else -> null
                }
                ChatRoomScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMedia = { groupId, messageId, index ->
                        navController.navigate(MediaViewerRoute(groupId, messageId, index))
                    },
                    onOpenPlace = { navController.navigate(PlaceDetailRoute(it)) },
                    onOpenSchedule = { navController.navigate(ScheduleDetailRoute(it)) },
                    onOpenInbox = { navController.navigate(CandidateInboxRoute(route.groupId)) },
                    focus = focus,
                    onFocusHandled = {
                        routeFocusDone = true
                        entry.savedStateHandle[FOCUS_CANDIDATE_KEY] = null
                    },
                )
            }
            composable<CandidateInboxRoute> {
                CandidateInboxScreen(
                    onBack = { navController.popBackStack() },
                    onOpenOriginal = { candidateId ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(FOCUS_CANDIDATE_KEY, candidateId)
                        navController.popBackStack()
                    },
                )
            }
            composable<MediaViewerRoute> { MediaViewerScreen(onClose = { navController.popBackStack() }) }
            composable<CalendarRoute> {
                CalendarScreen(
                    onOpenSchedule = { navController.navigate(ScheduleDetailRoute(it)) },
                    onAddSchedule = { date -> navController.navigate(ScheduleEditRoute(date = date.toString())) },
                    onOpenSource = navController::openSource,
                )
            }
            composable<ScheduleDetailRoute> {
                ScheduleDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(ScheduleEditRoute(scheduleId = it)) },
                    onOpenPlace = { navController.navigate(PlaceDetailRoute(it)) },
                    onOpenSource = navController::openSource,
                )
            }
            composable<ScheduleEditRoute> { entry ->
                val route = entry.toRoute<ScheduleEditRoute>()
                ScheduleEditScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = { scheduleId ->
                        // 등록하면 상세로 넘어가고, 수정이면 원래 상세로 돌아간다
                        if (route.scheduleId == 0L) {
                            navController.navigate(ScheduleDetailRoute(scheduleId)) {
                                popUpTo<ScheduleEditRoute> { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable<PlaceRoute> {
                PlaceScreen(
                    onOpenPlace = { navController.navigate(PlaceDetailRoute(it)) },
                    onAddPlace = { navController.navigate(PlaceSearchRoute) },
                    onOpenAliases = { navController.navigate(PlaceAliasRoute) },
                )
            }
            composable<PlaceAliasRoute> {
                PlaceAliasScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlace = { navController.navigate(PlaceDetailRoute(it)) },
                )
            }
            composable<PlaceSearchRoute> {
                PlaceSearchScreen(
                    onBack = { navController.popBackStack() },
                    // 공유했거나 이미 공유된 장소를 고르면 검색을 닫고 상세로
                    onOpenPlace = { placeId ->
                        navController.navigate(PlaceDetailRoute(placeId)) { popUpTo<PlaceSearchRoute> { inclusive = true } }
                    },
                )
            }
            composable<PlaceDetailRoute> {
                PlaceDetailScreen(onBack = { navController.popBackStack() }, onOpenSource = navController::openSource)
            }
            composable<AlbumRoute> { AlbumScreen() }
            composable<StoryRoute> { StoryScreen() }
        }
    }

    val showNotificationPrompt by viewModel.showNotificationPrompt.collectAsStateWithLifecycle()
    NotificationPermissionPrompt(
        visible = showNotificationPrompt && currentTab != null,
        onDone = viewModel::onNotificationPromptDone,
    )

    if (showSwitcher) {
        GroupSwitcherSheet(
            state = switcherState,
            onDismiss = { showSwitcher = false },
            onSelect = viewModel::switchTo,
            onInvite = viewModel::invite,
            onCreate = {
                showSwitcher = false
                navController.navigate(GroupStartRoute(GroupStartMode.CREATE, canGoBack = true))
            },
            onJoin = {
                showSwitcher = false
                navController.navigate(GroupStartRoute(GroupStartMode.JOIN, canGoBack = true))
            },
        )
    }
}

/** 탭 화면에서만 앱 바와 하단 탭을 보인다. 로그인·그룹 시작 등은 콘텐츠만 */
@Composable
fun MainScaffold(
    currentTab: TopLevelDestination?,
    groupTitle: String,
    onTabSelected: (TopLevelDestination) -> Unit,
    onGroupSwitcherClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (currentTab != null) {
                FamAppBar(
                    title = if (currentTab.showsGroupSwitcher) groupTitle else stringResource(R.string.app_bar_brand),
                    onGroupSwitcherClick = if (currentTab.showsGroupSwitcher) onGroupSwitcherClick else null,
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                FamBottomNav {
                    TopLevelDestination.entries.forEach { tab ->
                        FamBottomNavItem(
                            selected = tab == currentTab,
                            onClick = { onTabSelected(tab) },
                            icon = painterResource(tab.iconRes),
                            label = stringResource(tab.labelRes),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // 입력 화면의 imePadding이 이미 준 시스템 바 여백을 다시 더하지 않게 소비 처리한다
        content(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))
    }
}

private fun NavDestination.topLevelDestination(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { tab ->
        hierarchy.any { it.hasRoute(tab.route::class) }
    }

/** 시작 분기(시작·로그인·그룹 시작·역할 정하기)를 마친 화면 */
private fun NavDestination.isMainArea(): Boolean =
    listOf(StartRoute::class, LoginRoute::class, GroupStartRoute::class, RoleSetupRoute::class)
        .none { route -> hierarchy.any { it.hasRoute(route) } }

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        // 탭 화면의 바닥은 항상 채팅 탭이다 (시작·로그인 화면은 이동할 때 모두 지운다)
        popUpTo(ChatRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 시작 분기 결과로 이동한다. 이전 화면(로그인·가입·그룹 시작)으로 돌아가지 않게 스택을 비운다 */
private fun NavHostController.navigateTo(destination: StartDestination) {
    val route: Any = when (destination) {
        StartDestination.Login -> LoginRoute
        StartDestination.GroupStart -> GroupStartRoute()
        is StartDestination.RoleSetup -> RoleSetupRoute(destination.groupId)
        StartDestination.Main -> ChatRoute
        StartDestination.Unavailable -> return
    }
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** 일정·할 일·장소의 원문 보기 (ARC-06): 그 그룹 채팅방의 원문 메시지로 */
private fun NavHostController.openSource(groupId: Long, source: ItemSource) {
    navigate(ChatRoomRoute(groupId, focusSeq = source.seq, focusCandidateId = source.candidateId))
}

/** 제안함에서 [원문 보기]로 고른 후보를 채팅방에 넘기는 키. 라우트 인자(focusCandidateId)와 이름이 같으면 인자를 읽어 버린다 */
private const val FOCUS_CANDIDATE_KEY = "inboxPickedCandidateId"
