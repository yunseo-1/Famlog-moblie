package com.famlog.feature

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.Consent
import com.famlog.feature.auth.RestoreDialog
import com.famlog.feature.group.GroupStartContent
import com.famlog.feature.group.GroupStartMode
import com.famlog.feature.group.GroupStartUiState
import com.famlog.feature.settings.AccountDeleteContent
import com.famlog.feature.settings.AccountDeleteUiState
import com.famlog.feature.settings.PrivacyContent
import com.famlog.feature.settings.PrivacyUiState
import com.famlog.feature.settings.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 설정·개인정보·계정 삭제·복구 (SCR-80·81, ACC-01) */
@RunWith(AndroidJUnit4::class)
class AccountScreensTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 설정에는_개인정보_보안과_로그아웃과_앱_버전() {
        var privacy = false
        var logout = false
        compose.setContent { FamLogTheme { SettingsContent("1.0", onBack = {}, onOpenPrivacy = { privacy = true }, onLogout = { logout = true }) } }

        compose.onNodeWithText("개인정보·보안").performClick()
        compose.onNodeWithText("로그아웃").performClick()
        compose.onNodeWithText("앱 버전 1.0").assertExists()
        assertTrue(privacy && logout)
    }

    @Test
    fun 동의_내역에서_위치와_AI_분석만_철회할_수_있다() {
        var withdrawn: String? = null
        val consents = listOf(
            Consent(Consent.TERMS, true, "1.0", "1.0", false),
            Consent(Consent.LOCATION, true, "1.0", "1.0", false),
            Consent(Consent.AI_ANALYSIS, false, null, "1.0", false),
        )
        compose.setContent {
            FamLogTheme {
                PrivacyContent(
                    state = PrivacyUiState(consents = consents, loading = false),
                    onBack = {}, onRetry = {}, onWithdraw = { withdrawn = it }, onOpenWebDeletion = {}, onOpenAccountDelete = {},
                )
            }
        }

        compose.onNodeWithText("이용약관").assertExists()
        compose.onNodeWithText("AI 분석 안내 확인").assertExists()
        compose.onNodeWithText("동의 안 함").assertExists()
        // 철회 버튼은 동의한 위치기반서비스에만
        assertEquals(1, compose.onAllNodesWithText("철회").fetchSemanticsNodes().size)
        compose.onNodeWithText("철회").performClick()
        assertEquals(Consent.LOCATION, withdrawn)
        compose.onNodeWithText("웹에서 계정 삭제 안내").assertExists()
        compose.onNodeWithText("계정 삭제").assertExists()
    }

    @Test
    fun 계정_삭제_화면은_안내와_사진_지우기_선택과_삭제_버튼() {
        var media: Boolean? = null
        var deleted = false
        compose.setContent {
            FamLogTheme {
                AccountDeleteContent(AccountDeleteUiState(), onBack = {}, onDeleteMediaChange = { media = it }, onDelete = { deleted = true })
            }
        }

        compose.onNodeWithText("· 30일 안에 같은 계정으로 로그인하면 복구할 수 있어요.").assertExists()
        compose.onNode(isToggleable() and hasText("내가 올린 사진·동영상도 지우기", substring = true)).assertIsOff().performClick()
        assertEquals(true, media)
        compose.onAllNodesWithText("계정 삭제")[1].performClick()
        assertTrue(deleted)
    }

    @Test
    fun 삭제_예정_계정_로그인은_기한과_함께_복구를_묻는다() {
        var restored = false
        compose.setContent {
            FamLogTheme { RestoreDialog(UiText.Plain("삭제 예정인 계정이에요. 12월 21일까지 복구할 수 있어요."), false, { restored = true }, {}) }
        }

        compose.onNodeWithText("계정을 복구할까요?").assertExists()
        compose.onNodeWithText("삭제 예정인 계정이에요. 12월 21일까지 복구할 수 있어요.").assertExists()
        compose.onNodeWithText("복구하고 로그인").performClick()
        assertTrue(restored)
    }

    @Test
    fun 그룹이_없는_첫_화면에도_설정으로_가는_길이_있다() {
        var opened = false
        compose.setContent {
            FamLogTheme {
                GroupStartContent(
                    mode = GroupStartMode.CHOOSE, state = GroupStartUiState(), onBack = null, onModeChange = {},
                    onNameChange = {}, onCodeChange = {}, onCreate = {}, onJoin = {}, onOpenSettings = { opened = true },
                )
            }
        }

        compose.onNodeWithContentDescription("설정").performClick()
        assertTrue(opened)
    }
}
