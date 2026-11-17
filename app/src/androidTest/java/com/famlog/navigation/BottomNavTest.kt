package com.famlog.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.famlog.MainActivity
import org.junit.Rule
import org.junit.Test

class BottomNavTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun 하단_탭_5개를_전환하고_앱_바_제목이_탭에_맞게_바뀐다() {
        compose.onNodeWithText("채팅").assertIsSelected()
        compose.onNodeWithText("FamLog").assertIsDisplayed()

        compose.onNodeWithText("캘린더").performClick().assertIsSelected()
        compose.onNodeWithText("우리 가족").assertIsDisplayed()

        compose.onNodeWithText("장소").performClick().assertIsSelected()
        compose.onNodeWithText("이야기").performClick().assertIsSelected()
        compose.onNodeWithText("곧 만나요").assertIsDisplayed()

        compose.onNodeWithText("앨범").performClick().assertIsSelected()
        compose.onNodeWithText("곧 만나요").assertIsDisplayed()
    }
}
