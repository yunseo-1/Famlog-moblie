package com.famlog.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.LoginSession
import com.famlog.feature.settings.PrivacyContent
import com.famlog.feature.settings.PrivacyUiState
import com.famlog.feature.settings.SessionsContent
import com.famlog.feature.settings.SessionsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** 로그인 기기 관리 (AUTH-03) */
@RunWith(AndroidJUnit4::class)
class SessionsScreenTest {

    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-23T03:00:00Z")

    @Test
    fun 이_기기는_표시만_하고_다른_기기만_로그아웃시킬_수_있다() {
        var revoked: Long? = null
        val sessions = listOf(
            LoginSession(9, "Pixel 9", "ANDROID", Instant.parse("2026-11-22T12:00:00Z"), now.minusSeconds(10), current = true),
            LoginSession(7, null, "IOS", Instant.parse("2026-11-01T01:00:00Z"), now.minusSeconds(2 * 3600), current = false),
        )
        compose.setContent {
            FamLogTheme {
                SessionsContent(SessionsUiState(sessions = sessions, loading = false), onBack = {}, onRetry = {}, onRevoke = { revoked = it.id }, now = now)
            }
        }

        compose.onNodeWithText("Pixel 9").assertExists()
        compose.onNodeWithText("이 기기").assertExists()
        compose.onNodeWithText("마지막 접속 방금 전").assertExists()
        // 기기 이름이 없으면 플랫폼으로
        compose.onNodeWithText("iPhone").assertExists()
        compose.onNodeWithText("마지막 접속 2시간 전").assertExists()
        compose.onNodeWithText("처음 로그인 2026년 11월 1일").assertExists()

        compose.onAllNodesWithText("로그아웃").assertCountEquals(1)
        compose.onNodeWithText("로그아웃").performClick()
        assertEquals(7L, revoked)
    }

    @Test
    fun 개인정보_보안에서_로그인_기기로_간다() {
        var opened = false
        compose.setContent {
            FamLogTheme {
                PrivacyContent(
                    state = PrivacyUiState(loading = false),
                    onBack = {}, onRetry = {}, onWithdraw = {}, onOpenWebDeletion = {}, onOpenAccountDelete = {},
                    onOpenSessions = { opened = true },
                )
            }
        }

        compose.onNodeWithText("로그인 기기").performClick()
        assertTrue(opened)
    }
}
