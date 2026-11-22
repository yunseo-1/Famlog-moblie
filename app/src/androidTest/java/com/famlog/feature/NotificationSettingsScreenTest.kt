package com.famlog.feature

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.NotificationSettings
import com.famlog.feature.settings.GroupNotification
import com.famlog.feature.settings.NotificationSettingsActions
import com.famlog.feature.settings.NotificationSettingsContent
import com.famlog.feature.settings.NotificationSettingsUiState
import com.famlog.feature.settings.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** 알림 설정 (SET-02) */
@RunWith(AndroidJUnit4::class)
class NotificationSettingsScreenTest {

    @get:Rule val compose = createComposeRule()


    @Test
    fun 방해_금지를_켜면_시각이_보이고_그룹별로_알림을_끌_수_있다() {
        var preview: Boolean? = null
        var group: Pair<Long, Boolean>? = null
        compose.setContent {
            FamLogTheme {
                NotificationSettingsContent(
                    state = NotificationSettingsUiState(
                        settings = NotificationSettings(quietHoursEnabled = true, quietStart = LocalTime.of(22, 0), quietEnd = LocalTime.of(7, 0)),
                        groups = listOf(GroupNotification(1, "우리가족", false), GroupNotification(2, "외가", true), GroupNotification(3, "친가", null)),
                        loading = false,
                    ),
                    actions = NotificationSettingsActions(
                        onPreviewHiddenChange = { preview = it },
                        onGroupEnabledChange = { id, enabled -> group = id to enabled },
                    ),
                )
            }
        }

        compose.onNodeWithText("오후 10:00").assertExists()
        compose.onNodeWithText("오전 7:00").assertExists()
        compose.onNode(hasText("미리보기 숨기기") and isToggleable()).assertIsOff().performClick()
        assertEquals(true, preview)

        compose.onNode(hasText("우리가족") and isToggleable()).performScrollTo().assertIsOn().performClick()
        assertEquals(1L to false, group)
        compose.onNode(hasText("외가") and isToggleable()).performScrollTo().assertIsOff()
        compose.onNode(hasText("친가") and isToggleable()).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun 방해_금지가_꺼져_있으면_시각을_숨긴다() {
        compose.setContent {
            FamLogTheme {
                NotificationSettingsContent(NotificationSettingsUiState(loading = false), NotificationSettingsActions())
            }
        }

        compose.onNodeWithText("오후 10:00").assertDoesNotExist()
    }

    @Test
    fun 설정에서_알림으로_간다() {
        var opened = false
        compose.setContent {
            FamLogTheme { SettingsContent("1.0", onBack = {}, onOpenPrivacy = {}, onLogout = {}, onOpenNotifications = { opened = true }) }
        }

        compose.onNodeWithText("알림").performClick()
        assertTrue(opened)
    }
}
