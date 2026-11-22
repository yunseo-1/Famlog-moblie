package com.famlog.feature

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.feature.settings.AppLockContent
import com.famlog.feature.settings.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 앱 잠금 (SEC-01) */
@RunWith(AndroidJUnit4::class)
class AppLockScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 잠금_화면은_잠금_해제_버튼으로_인증을_다시_띄운다() {
        var unlock = 0
        compose.setContent { FamLogTheme { AppLockContent(onUnlock = { unlock++ }) } }

        compose.onNodeWithText("FamLog가 잠겨 있어요").assertExists()
        compose.onNodeWithText("잠금 해제").performClick()
        assertEquals(1, unlock)
    }

    @Test
    fun 설정의_앱_잠금_스위치() {
        var requested: Boolean? = null
        compose.setContent {
            FamLogTheme {
                SettingsContent("1.0", onBack = {}, onOpenPrivacy = {}, onLogout = {}, appLockEnabled = false, onAppLockChange = { requested = it })
            }
        }

        compose.onNode(hasText("앱 잠금") and isToggleable()).assertIsOff().performClick()
        assertEquals(true, requested)
    }

    @Test
    fun 켜져_있으면_스위치가_켜져_보인다() {
        compose.setContent {
            FamLogTheme { SettingsContent("1.0", onBack = {}, onOpenPrivacy = {}, onLogout = {}, appLockEnabled = true) }
        }

        compose.onNode(hasText("앱 잠금") and isToggleable()).assertIsOn()
    }
}
