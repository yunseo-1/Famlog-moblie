package com.famlog.feature

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.ReportReason
import com.famlog.core.model.ReportTarget
import com.famlog.core.model.ReportTargetType
import com.famlog.feature.report.ReportContent
import com.famlog.feature.report.ReportSheetActions
import com.famlog.feature.report.ReportSheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 신고하기 (MOD-01, SCR-82) */
@RunWith(AndroidJUnit4::class)
class ReportScreensTest {

    @get:Rule val compose = createComposeRule()

    private fun show(state: ReportSheetState, actions: ReportSheetActions = ReportSheetActions()) {
        compose.setContent { FamLogTheme { ReportContent(state, actions) } }
    }

    @Test
    fun 메시지_신고는_사유를_고르고_AI_사유는_없다() {
        var picked: ReportReason? = null
        var submitted = false
        show(
            ReportSheetState(ReportTarget(ReportTargetType.MESSAGE, 30)),
            ReportSheetActions(onReasonChange = { picked = it }, onSubmit = { submitted = true }),
        )

        compose.onNode(hasText("신고하기") and isHeading()).assertExists()
        compose.onNodeWithText("이 메시지를 신고하는 이유를 골라 주세요. 신고한 사람은 알리지 않아요.").assertExists()
        compose.onNodeWithText("AI 제안이 틀렸어요").assertDoesNotExist()
        compose.onNodeWithText("괴롭힘·욕설").performClick()
        assertEquals(ReportReason.HARASSMENT, picked)
        compose.onNodeWithText("자세한 내용 (선택)").assertExists()

        compose.onNode(hasText("신고하기") and hasClickAction()).performScrollTo().performClick()
        assertTrue(submitted)
    }

    @Test
    fun AI_제안_신고는_AI_사유가_있고_기타는_내용이_필수다() {
        show(
            ReportSheetState(
                ReportTarget(ReportTargetType.AI_OUTPUT, 9), reason = ReportReason.OTHER, showErrors = true,
            ),
        )

        compose.onNodeWithText("AI 제안이 틀렸어요").assertExists()
        compose.onNodeWithText("자세한 내용 (필수)").assertExists()
        compose.onNodeWithText("기타를 고르면 내용을 적어 주세요.").performScrollTo().assertExists()
    }
}
