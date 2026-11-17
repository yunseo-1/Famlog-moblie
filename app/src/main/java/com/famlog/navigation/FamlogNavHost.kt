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
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamAppBar
import com.famlog.core.designsystem.component.FamBottomNav
import com.famlog.core.designsystem.component.FamBottomNavItem
import com.famlog.core.network.SessionEvent
import com.famlog.feature.album.AlbumScreen
import com.famlog.feature.auth.LoginScreen
import com.famlog.feature.calendar.CalendarScreen
import com.famlog.feature.chat.ChatRoomScreen
import com.famlog.feature.chat.ChatScreen
import com.famlog.feature.group.GroupStartMode
import com.famlog.feature.group.GroupStartScreen
import com.famlog.feature.group.GroupSwitcherSheet
import com.famlog.feature.group.RoleSetupScreen
import com.famlog.feature.group.shareInvite
import com.famlog.feature.place.PlaceScreen
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
            composable<ChatRoomRoute> { ChatRoomScreen(onBack = { navController.popBackStack() }) }
            composable<CalendarRoute> { CalendarScreen() }
            composable<PlaceRoute> { PlaceScreen() }
            composable<AlbumRoute> { AlbumScreen() }
            composable<StoryRoute> { StoryScreen() }
        }
    }

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
