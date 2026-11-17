package com.famlog.feature

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.FamilyRole
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.feature.auth.AuthStep
import com.famlog.feature.auth.AuthUiState
import com.famlog.feature.auth.BirthCheck
import com.famlog.feature.auth.SignupContent
import com.famlog.feature.group.GroupSwitcherContent
import com.famlog.feature.group.GroupSwitcherState
import com.famlog.feature.group.RoleSetupContent
import com.famlog.feature.group.RoleSetupUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** SCR-01 가입 단계, SCR-03, SCR-70 화면 (상태만 넣어 그린다) */
class AuthGroupScreensTest {

    @get:Rule val compose = createComposeRule()

    private val signupReady = AuthUiState(
        step = AuthStep.SIGNUP,
        agreeTerms = true,
        agreePrivacy = true,
        nickname = "윤서",
        birthYm = "199003",
        birthCheck = BirthCheck.OK,
    )

    private fun setSignup(state: AuthUiState, onSignup: () -> Unit = {}) = compose.setContent {
        FamLogTheme {
            SignupContent(state, {}, {}, {}, {}, {}, {}, onSignup = onSignup)
        }
    }

    @Test
    fun 필수_동의가_빠지면_가입하기를_누를_수_없다() {
        setSignup(signupReady.copy(agreePrivacy = false))
        compose.onNodeWithText("가입하기").assertIsNotEnabled()
    }

    @Test
    fun 만_14세_미만이면_안내가_보이고_가입하기를_누를_수_없다() {
        setSignup(signupReady.copy(birthYm = "201301", birthCheck = BirthCheck.UNDER_AGE))
        compose.onNodeWithText("만 14세 이상만 가입할 수 있어요", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("가입하기").assertIsNotEnabled()
    }

    @Test
    fun 모두_채우면_가입하기를_누를_수_있다() {
        var clicks = 0
        setSignup(signupReady) { clicks++ }
        compose.onNodeWithText("가입하기").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun 역할을_고르면_선택_표시되고_시작하기를_누를_수_있다() {
        compose.setContent {
            FamLogTheme {
                RoleSetupContent(
                    state = RoleSetupUiState(
                        loading = false,
                        groupName = "우리 가족",
                        familyRole = FamilyRole.DAUGHTER,
                        calendarColor = "#14A3B8",
                    ),
                    onRoleSelect = {}, onTitleChange = {}, onColorSelect = {}, onSave = {}, onRetry = {},
                )
            }
        }
        compose.onNodeWithText("딸").assertIsSelected()
        compose.onNodeWithContentDescription("캘린더 색 1").performScrollTo().assertIsSelected()
        compose.onNodeWithText("시작하기").assertIsEnabled()
    }

    @Test
    fun 그룹_전환_시트는_현재_그룹을_표시하고_그룹장에게만_초대를_보인다() {
        var selected: Long? = null
        val groups = listOf(
            GroupSummary(1, "우리 가족", 4, GroupRole.OWNER),
            GroupSummary(2, "외가", 6, GroupRole.MEMBER),
        )
        compose.setContent {
            FamLogTheme {
                GroupSwitcherContent(
                    state = GroupSwitcherState(groups = groups, currentGroupId = 2),
                    onSelect = { selected = it }, onInvite = {}, onCreate = {}, onJoin = {},
                )
            }
        }
        compose.onNodeWithText("가족 그룹 선택").assertIsDisplayed()
        compose.onNodeWithContentDescription("현재 그룹").assertIsDisplayed()
        compose.onNodeWithText("가족 초대하기", substring = true).assertDoesNotExist() // 외가에서는 구성원
        compose.onNodeWithText("새 가족 그룹 만들기").assertIsDisplayed()

        compose.onNodeWithText("우리 가족").performClick()
        assertEquals(1L, selected)
    }
}
