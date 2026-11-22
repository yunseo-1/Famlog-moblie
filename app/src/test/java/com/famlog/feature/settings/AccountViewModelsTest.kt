package com.famlog.feature.settings

import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.AppLock
import com.famlog.core.data.AuthRepository
import com.famlog.core.data.ConsentRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.Consent
import com.famlog.core.model.ErrorCodes
import com.famlog.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 설정·개인정보·계정 삭제 (SCR-80·81, ACC-01) */
class AccountViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val auth = mockk<AuthRepository>(relaxed = true)
    private val consents = mockk<ConsentRepository>()
    private val appLock = mockk<AppLock>(relaxed = true)

    private fun consent(type: String, agreed: Boolean = true) = Consent(type, agreed, "1.0".takeIf { agreed }, "1.0", false)

    @Test
    fun 로그아웃은_확인한_뒤에만_한다() = runTest(mainRule.dispatcher) {
        val viewModel = SettingsViewModel(auth, appLock, "1.0")

        viewModel.askLogout()
        assertTrue(viewModel.confirmLogout.value)
        viewModel.cancelLogout()
        coVerify(exactly = 0) { auth.logout() }

        viewModel.askLogout()
        viewModel.logout()
        coVerify(exactly = 1) { auth.logout() }
        assertFalse(viewModel.confirmLogout.value)
    }

    @Test
    fun 앱_잠금은_화면이_기기_인증을_받은_뒤_켜고_끈다() = runTest(mainRule.dispatcher) {
        val viewModel = SettingsViewModel(auth, appLock, "1.0")

        viewModel.setAppLock(true)
        viewModel.setAppLock(false)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            appLock.setEnabled(true)
            appLock.setEnabled(false)
        }
    }

    @Test
    fun 동의_내역은_아는_종류만_정해진_순서로_보이고_웹_삭제_안내는_서버_루트() = runTest(mainRule.dispatcher) {
        coEvery { consents.all() } returns ApiResult.Success(
            listOf(consent(Consent.LOCATION), consent("MARKETING"), consent(Consent.TERMS), consent(Consent.AI_ANALYSIS, agreed = false)),
        )

        val viewModel = PrivacyViewModel(consents, "http://10.0.2.2:8080/api/v1/")

        assertEquals(listOf(Consent.TERMS, Consent.LOCATION, Consent.AI_ANALYSIS), viewModel.state.value.consents.map { it.type })
        assertEquals("http://10.0.2.2:8080/account-deletion", viewModel.webDeletionUrl)
    }

    @Test
    fun 위치와_AI_분석만_철회할_수_있고_확인하면_철회한_뒤_다시_받는다() = runTest(mainRule.dispatcher) {
        coEvery { consents.all() } returns ApiResult.Success(listOf(consent(Consent.TERMS), consent(Consent.AI_ANALYSIS)))
        coEvery { consents.withdrawAiNotice() } returns ApiResult.Success(consent(Consent.AI_ANALYSIS, agreed = false))
        val viewModel = PrivacyViewModel(consents, "http://x/api/v1/")

        viewModel.askWithdraw(Consent.TERMS)
        assertNull(viewModel.state.value.withdraw)

        viewModel.askWithdraw(Consent.AI_ANALYSIS)
        viewModel.events.test {
            viewModel.confirmWithdraw()
            assertEquals(UiText.Res(R.string.privacy_withdrawn), awaitItem())
        }
        coVerify { consents.withdrawAiNotice() }
        coVerify(exactly = 2) { consents.all() }
        assertNull(viewModel.state.value.withdraw)
    }

    @Test
    fun 계정_삭제는_확인_뒤_사진_지우기_선택을_보낸다() = runTest(mainRule.dispatcher) {
        coEvery { auth.deleteAccount(true) } returns ApiResult.Success(Unit)
        val viewModel = AccountDeleteViewModel(auth)

        viewModel.setDeleteMedia(true)
        viewModel.ask()
        assertTrue(viewModel.state.value.confirm)
        viewModel.delete()

        coVerify { auth.deleteAccount(true) }
        assertFalse(viewModel.state.value.confirm)
    }

    @Test
    fun 그룹장이라_막히면_그룹_이름이_든_서버_문구로_넘기라고_안내한다() = runTest(mainRule.dispatcher) {
        coEvery { auth.deleteAccount(false) } returns
            ApiResult.Failure(409, ApiError(ErrorCodes.OWNER_TRANSFER_REQUIRED, "'우리 가족' 그룹의 그룹장을 다른 가족에게 넘긴 뒤 삭제할 수 있어요.", null))
        val viewModel = AccountDeleteViewModel(auth)

        viewModel.ask()
        viewModel.delete()

        assertEquals(UiText.Plain("'우리 가족' 그룹의 그룹장을 다른 가족에게 넘긴 뒤 삭제할 수 있어요."), viewModel.state.value.ownerTransfer)
        viewModel.dismissOwnerTransfer()
        assertNull(viewModel.state.value.ownerTransfer)
    }
}
