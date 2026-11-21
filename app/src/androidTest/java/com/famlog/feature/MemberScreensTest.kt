package com.famlog.feature

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.feature.group.MemberAction
import com.famlog.feature.group.MemberSheetActions
import com.famlog.feature.group.MemberSheetContent
import com.famlog.feature.group.MemberSheetState
import com.famlog.feature.group.memberActions
import com.famlog.feature.settings.DeleteDialog
import com.famlog.feature.settings.DeleteDialogView
import com.famlog.feature.settings.GroupSettingsContent
import com.famlog.feature.settings.GroupSettingsUiState
import com.famlog.feature.settings.LeaveDialog
import com.famlog.feature.settings.LeaveDialogView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 구성원 관리·그룹 나가기·삭제 (GRP-05) */
@RunWith(AndroidJUnit4::class)
class MemberScreensTest {

    @get:Rule val compose = createComposeRule()

    private val mom = Member(8, "김엄마", GroupRole.ADMIN, null, "엄마", null)

    private fun sheet(myRole: GroupRole, onAction: (MemberAction) -> Unit = {}) {
        val state = MemberSheetState(mom, memberActions(7, myRole, mom))
        compose.setContent { FamLogTheme { MemberSheetContent(state, MemberSheetActions(onAction = onAction)) } }
    }

    @Test
    fun 그룹장은_관리자에게_권한_넘기기_내보내기_신고를_할_수_있다() {
        var picked: MemberAction? = null
        sheet(GroupRole.OWNER) { picked = it }

        compose.onNodeWithText("엄마").assertExists()
        compose.onNodeWithText("관리자").assertExists()
        compose.onNodeWithText("구성원으로 바꾸기").assertExists()
        compose.onNodeWithText("그룹장 넘기기").assertExists()
        compose.onNodeWithText("신고하기").assertExists()
        compose.onNodeWithText("내보내기").performClick()
        assertEquals(MemberAction.REMOVE, picked)
    }

    @Test
    fun 구성원은_신고만_할_수_있다() {
        sheet(GroupRole.MEMBER)

        compose.onNodeWithText("신고하기").assertExists()
        compose.onNodeWithText("구성원으로 바꾸기").assertDoesNotExist()
        compose.onNodeWithText("그룹장 넘기기").assertDoesNotExist()
        compose.onNodeWithText("내보내기").assertDoesNotExist()
    }

    private fun settings(role: GroupRole) {
        compose.setContent {
            FamLogTheme {
                GroupSettingsContent(
                    state = GroupSettingsUiState(groupName = "우리 가족", myRole = role, loading = false, memberCount = 2),
                    onBack = {}, onRetry = {}, onAiEnabledChange = {}, onAiOptOutChange = {}, onShowNotice = {},
                )
            }
        }
    }

    @Test
    fun 그룹장에게만_그룹_삭제가_보인다() {
        settings(GroupRole.OWNER)
        compose.onNodeWithText("그룹 나가기").performScrollTo().assertExists()
        compose.onNodeWithText("그룹 삭제").performScrollTo().assertExists()
    }

    @Test
    fun 구성원은_나가기만_있다() {
        settings(GroupRole.MEMBER)
        compose.onNodeWithText("그룹 나가기").performScrollTo().assertExists()
        compose.onNodeWithText("그룹 삭제").assertDoesNotExist()
    }

    @Test
    fun 다른_가족이_있는_그룹장의_나가기는_넘기라는_안내와_확인만() {
        compose.setContent { FamLogTheme { LeaveDialogView("우리 가족", LeaveDialog(ownerMustTransfer = true), {}, {}) } }

        compose.onNodeWithText("그룹장은 나가기 전에 다른 가족에게 그룹장을 넘겨야 해요. 채팅방 메뉴에서 가족을 눌러 넘길 수 있어요.").assertExists()
        compose.onNodeWithText("확인").assertExists()
        compose.onNodeWithText("나가기").assertDoesNotExist()
    }

    @Test
    fun 그룹_삭제는_이름이_맞아야_누를_수_있다() {
        compose.setContent {
            FamLogTheme { DeleteDialogView("우리 가족", DeleteDialog("우리"), canDelete = false, onNameChange = {}, onConfirm = {}, onDismiss = {}) }
        }
        compose.onNodeWithText("삭제").assertIsNotEnabled()
    }

    @Test
    fun 그룹_이름을_맞게_입력하면_삭제를_누를_수_있다() {
        compose.setContent {
            FamLogTheme { DeleteDialogView("우리 가족", DeleteDialog("우리 가족"), canDelete = true, onNameChange = {}, onConfirm = {}, onDismiss = {}) }
        }
        compose.onNodeWithText("삭제").assertIsEnabled()
    }
}
