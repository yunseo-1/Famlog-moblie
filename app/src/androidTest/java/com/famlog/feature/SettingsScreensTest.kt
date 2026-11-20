package com.famlog.feature

import androidx.compose.ui.test.assertIsEnabled
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
import com.famlog.core.model.AiNotice
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.feature.settings.AiNoticeContent
import com.famlog.feature.settings.AiNoticeMode
import com.famlog.feature.settings.AiNoticeSheetState
import com.famlog.feature.settings.GroupSettingsContent
import com.famlog.feature.settings.GroupSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 그룹 설정 · AI 분석 안내 (SET-01) */
@RunWith(AndroidJUnit4::class)
class SettingsScreensTest {

    @get:Rule val compose = createComposeRule()

    private val notice = AiNotice(
        "1.0", "Anthropic", "미국", listOf("채팅 본문(전화·계좌 가림)", "보낸 시각"), listOf("사진·동영상"),
        "등록 후보 만들기", "처리 후 바로 지움", "그룹 설정에서 내 메시지 분석 제외",
    )
    private val me = Member(7, "윤서", GroupRole.OWNER, null, "딸", null, aiOptOut = false)
    private val mom = Member(8, "김엄마", GroupRole.MEMBER, null, "엄마", null, aiOptOut = true)

    private fun settings(state: GroupSettingsUiState, onAi: (Boolean) -> Unit = {}, onOptOut: (Boolean) -> Unit = {}) {
        compose.setContent {
            FamLogTheme {
                GroupSettingsContent(
                    state = state, onBack = {}, onRetry = {}, onAiEnabledChange = onAi, onAiOptOutChange = onOptOut, onShowNotice = {},
                )
            }
        }
    }

    @Test
    fun 관리자는_AI를_켜고_구성원별_분석_제외를_본다() {
        var ai: Boolean? = null
        settings(
            GroupSettingsUiState(myRole = GroupRole.OWNER, myUserId = 7, aiEnabled = false, members = listOf(me, mom), loading = false),
            onAi = { ai = it },
        )

        compose.onNode(isToggleable() and hasText("대화 분석", substring = true)).assertIsOff().assertIsEnabled().performClick()
        assertEquals(true, ai)
        compose.onNodeWithText("구성원별 분석 제외").assertExists()
        compose.onNodeWithText("관리자도 볼 수만 있고 바꿀 수 없어요").assertExists()
        compose.onNodeWithText("엄마").assertExists()
        compose.onNodeWithText("제외").assertExists()
        compose.onNodeWithText("분석").assertExists()
    }

    @Test
    fun 구성원은_AI를_바꿀_수_없고_내_메시지만_제외한다() {
        var optOut: Boolean? = null
        settings(
            GroupSettingsUiState(myRole = GroupRole.MEMBER, myUserId = 8, aiEnabled = true, aiOptOut = false, loading = false),
            onOptOut = { optOut = it },
        )

        compose.onNode(isToggleable() and hasText("그룹장·관리자만 켜고 끌 수 있어요", substring = true)).assertIsOn().assertIsNotEnabled()
        compose.onNode(isToggleable() and hasText("내 메시지 분석 제외", substring = true)).assertIsOff().performClick()
        assertEquals(true, optOut)
        compose.onNodeWithText("구성원별 분석 제외").assertDoesNotExist()
    }

    private fun notice(mode: AiNoticeMode, onConfirm: () -> Unit = {}, onOptOut: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            FamLogTheme { AiNoticeContent(AiNoticeSheetState(notice, mode), onConfirm, onOptOut, onDismiss) }
        }
    }

    @Test
    fun 구성원_안내는_보내는_곳과_항목을_보이고_확인이나_분석_제외를_고른다() {
        var confirmed = false
        var optedOut = false
        notice(AiNoticeMode.CONFIRM, onConfirm = { confirmed = true }, onOptOut = { optedOut = true })

        compose.onNodeWithText("이 그룹은 AI 분석이 켜져 있어요").assertExists()
        compose.onNodeWithText("Anthropic (미국)").assertExists()
        compose.onNodeWithText("· 채팅 본문(전화·계좌 가림)\n· 보낸 시각").assertExists()
        compose.onNodeWithText("· 사진·동영상").assertExists()
        compose.onNodeWithText("그룹 설정에서 내 메시지 분석 제외").performScrollTo().assertExists()
        compose.onNodeWithText("안내 버전 1.0").performScrollTo().assertExists()

        compose.onNodeWithText("확인").performScrollTo().performClick()
        compose.onNodeWithText("내 메시지 분석 제외").performScrollTo().performClick()
        assertTrue(confirmed)
        assertTrue(optedOut)
    }

    @Test
    fun 켜기_전_안내는_동의하고_켜기와_취소() {
        var confirmed = false
        var cancelled = false
        notice(AiNoticeMode.ENABLE, onConfirm = { confirmed = true }, onDismiss = { cancelled = true })

        compose.onNodeWithText("AI 분석을 켜기 전에 확인해 주세요").assertExists()
        compose.onNodeWithText("동의하고 켜기").performScrollTo().performClick()
        compose.onNodeWithText("취소").performScrollTo().performClick()
        compose.onNodeWithText("내 메시지 분석 제외").assertDoesNotExist()
        assertTrue(confirmed)
        assertTrue(cancelled)
    }
}
