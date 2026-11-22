package com.famlog.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.model.DataExport
import com.famlog.core.model.DataExportStatus
import com.famlog.feature.settings.DataExportContent
import com.famlog.feature.settings.DataExportUiState
import com.famlog.feature.settings.PrivacyContent
import com.famlog.feature.settings.PrivacyUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** 내 데이터 내보내기 (ACC-02) */
@RunWith(AndroidJUnit4::class)
class DataExportScreenTest {

    @get:Rule val compose = createComposeRule()

    private var requested = false
    private var downloaded = false

    private fun show(export: DataExport?) {
        compose.setContent {
            FamLogTheme {
                DataExportContent(
                    state = DataExportUiState(export = export, loading = false),
                    onBack = {}, onRetry = {}, onRequest = { requested = true }, onDownload = { downloaded = true },
                )
            }
        }
    }

    private fun export(status: DataExportStatus) = DataExport(
        id = 3,
        status = status,
        requestedAt = Instant.parse("2026-11-22T01:00:00Z"),
        expiresAt = Instant.parse("2026-11-29T01:01:00Z"),
        sizeBytes = 2048,
    )

    @Test
    fun 개인정보_보안에서_내보내기로_간다() {
        var opened = false
        compose.setContent {
            FamLogTheme {
                PrivacyContent(
                    state = PrivacyUiState(loading = false),
                    onBack = {}, onRetry = {}, onWithdraw = {}, onOpenWebDeletion = {}, onOpenAccountDelete = {},
                    onOpenDataExport = { opened = true },
                )
            }
        }

        compose.onNodeWithText("내 데이터 내보내기").performClick()
        assertTrue(opened)
    }

    @Test
    fun 요청한_적이_없으면_들어가는_것을_안내하고_요청할_수_있다() {
        show(null)

        compose.onNodeWithText("다른 가족의 메시지·사진은 들어가지 않아요", substring = true).assertExists()
        compose.onNodeWithText("아직 요청한 적이 없어요").assertExists()
        compose.onNodeWithText("내보내기 요청").performClick()
        assertTrue(requested)
    }

    @Test
    fun 준비_중이면_버튼_없이_기다린다() {
        show(export(DataExportStatus.PROCESSING))

        compose.onNodeWithText("파일을 만들고 있어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("내보내기 요청").assertDoesNotExist()
        compose.onNodeWithText("내려받기").assertDoesNotExist()
    }

    @Test
    fun 준비되면_크기와_기한을_보여주고_내려받는다() {
        show(export(DataExportStatus.READY))

        compose.onNodeWithText("파일이 준비됐어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("11월 29일 오전 10:01까지", substring = true).assertExists()
        compose.onNodeWithText("내려받기").performClick()
        assertTrue(downloaded)
        compose.onNodeWithText("내보내기 요청").assertDoesNotExist()
    }

    @Test
    fun 실패하면_다시_요청한다() {
        show(export(DataExportStatus.FAILED))

        compose.onNodeWithText("파일을 만들지 못했어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 요청").performClick()
        assertTrue(requested)
    }

    @Test
    fun 기간이_지나면_새로_요청한다() {
        show(export(DataExportStatus.EXPIRED))

        compose.onNodeWithText("지난 파일은 기간이 지나 지워졌어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("내보내기 요청").performClick()
        assertTrue(requested)
    }
}
