package com.famlog.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.famlog.R
import com.famlog.core.designsystem.component.FamAppBar
import com.famlog.core.designsystem.component.FamBottomNav
import com.famlog.core.designsystem.component.FamBottomNavItem
import com.famlog.core.network.SessionEvent
import com.famlog.feature.album.AlbumScreen
import com.famlog.feature.auth.LoginScreen
import com.famlog.feature.calendar.CalendarScreen
import com.famlog.feature.chat.ChatScreen
import com.famlog.feature.place.PlaceScreen
import com.famlog.feature.story.StoryScreen

@Composable
fun FamlogNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel, navController) {
        viewModel.sessionEvents.collect { event ->
            when (event) {
                SessionEvent.LoggedOut -> navController.navigate(LoginRoute) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination?.topLevelDestination()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentTab != null) {
                FamAppBar(
                    title = if (currentTab.showsGroupSwitcher) {
                        // TODO(S7, GRP-03): 현재 그룹 이름으로 바꾸고 그룹 전환 바텀시트(SCR-70) 열기
                        stringResource(R.string.app_bar_current_group_placeholder)
                    } else {
                        stringResource(R.string.app_bar_brand)
                    },
                    onGroupSwitcherClick = if (currentTab.showsGroupSwitcher) ({}) else null,
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                FamBottomNav {
                    TopLevelDestination.entries.forEach { tab ->
                        FamBottomNavItem(
                            selected = tab == currentTab,
                            onClick = { navController.navigateToTab(tab) },
                            icon = painterResource(tab.iconRes),
                            label = stringResource(tab.labelRes),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            // TODO(S7, AUTH-01): 토큰이 없으면 로그인, 있으면 마지막 그룹으로 시작
            startDestination = ChatRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<ChatRoute> { ChatScreen() }
            composable<CalendarRoute> { CalendarScreen() }
            composable<PlaceRoute> { PlaceScreen() }
            composable<AlbumRoute> { AlbumScreen() }
            composable<StoryRoute> { StoryScreen() }
            composable<LoginRoute> { LoginScreen() }
        }
    }
}

private fun NavDestination.topLevelDestination(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { tab ->
        hierarchy.any { it.hasRoute(tab.route::class) }
    }

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
