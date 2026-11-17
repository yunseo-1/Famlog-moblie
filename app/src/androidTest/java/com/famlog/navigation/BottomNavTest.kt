package com.famlog.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.famlog.core.designsystem.theme.FamLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BottomNavTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 탭을_바꾸면_앱_바_제목이_채팅은_FamLog_나머지는_현재_그룹_이름() {
        var switcherClicks = 0
        compose.setContent {
            FamLogTheme {
                var tab by remember { mutableStateOf(TopLevelDestination.CHAT) }
                MainScaffold(
                    currentTab = tab,
                    groupTitle = "외가",
                    onTabSelected = { tab = it },
                    onGroupSwitcherClick = { switcherClicks++ },
                ) {}
            }
        }

        compose.onNodeWithText("채팅").assertIsSelected()
        compose.onNodeWithText("FamLog").assertIsDisplayed()

        compose.onNodeWithText("캘린더").performClick().assertIsSelected()
        compose.onNodeWithText("외가").assertIsDisplayed().performClick()
        assertEquals(1, switcherClicks)

        compose.onNodeWithText("장소").performClick().assertIsSelected()
        compose.onNodeWithText("앨범").performClick().assertIsSelected()
        compose.onNodeWithText("이야기").performClick().assertIsSelected()
        compose.onNodeWithText("외가").assertIsDisplayed()
    }

    @Test
    fun 탭_밖_화면에는_앱_바와_하단_탭이_없다() {
        compose.setContent {
            FamLogTheme {
                MainScaffold(currentTab = null, groupTitle = "외가", onTabSelected = {}, onGroupSwitcherClick = {}) {}
            }
        }
        compose.onNodeWithText("채팅").assertDoesNotExist()
        compose.onNodeWithText("외가").assertDoesNotExist()
    }
}
