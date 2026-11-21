package com.famlog.feature

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.data.StartPrompt
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.GroupRole
import com.famlog.feature.settings.GroupSettingsContent
import com.famlog.feature.settings.GroupSettingsUiState
import com.famlog.navigation.ForceUpdateScreen
import com.famlog.navigation.StartPromptDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 앱 설정·강제 업데이트 (SCR-00) */
@RunWith(AndroidJUnit4::class)
class AppConfigScreensTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 강제_업데이트_화면은_안내와_업데이트_버튼만_있다() {
        var opened = false
        compose.setContent { FamLogTheme { ForceUpdateScreen(onOpenStore = { opened = true }) } }

        compose.onNodeWithText("새 버전으로 업데이트해 주세요").assertExists()
        compose.onNodeWithText("이 버전은 더 이상 쓸 수 없어요. 업데이트하면 대화와 일정은 그대로 이어져요.").assertExists()
        compose.onNode(hasText("업데이트") and hasClickAction()).performClick()
        assertTrue(opened)
    }

    @Test
    fun 공지는_확인으로_닫는다() {
        var done = 0
        compose.setContent { FamLogTheme { StartPromptDialog(StartPrompt.Notice("11/25 새벽 2시~4시 점검이 있어요.", "t1"), onDone = { done++ }) } }

        compose.onNodeWithText("알려 드려요").assertExists()
        compose.onNodeWithText("11/25 새벽 2시~4시 점검이 있어요.").assertExists()
        compose.onNodeWithText("확인").performClick()
        assertEquals(1, done)
    }

    @Test
    fun 선택_업데이트는_업데이트하면_스토어를_열고_나중에는_닫기만() {
        var done = 0
        var opened = 0
        compose.setContent {
            FamLogTheme { StartPromptDialog(StartPrompt.OptionalUpdate, onDone = { done++ }, onOpenStore = { opened++ }) }
        }

        compose.onNodeWithText("새 버전이 있어요").assertExists()
        compose.onNodeWithText("나중에").performClick()
        assertEquals(1, done)
        assertEquals(0, opened)
        compose.onNodeWithText("업데이트").performClick()
        assertEquals(2, done)
        assertEquals(1, opened)
    }

    @Test
    fun AI_분석이_멈춰_있으면_그룹_설정에_안내를_보인다() {
        compose.setContent {
            FamLogTheme {
                GroupSettingsContent(
                    state = GroupSettingsUiState(myRole = GroupRole.OWNER, aiEnabled = true, aiPaused = true, loading = false),
                    onBack = {}, onRetry = {}, onAiEnabledChange = {}, onAiOptOutChange = {}, onShowNotice = {},
                )
            }
        }

        compose.onNodeWithText("AI 분석이 잠시 멈춰 있어요. 설정은 그대로 저장돼요.").assertExists()
    }
}
