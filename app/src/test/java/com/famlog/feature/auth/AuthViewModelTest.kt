package com.famlog.feature.auth

import android.content.Context
import app.cash.turbine.test
import com.famlog.R
import com.famlog.core.data.AuthRepository
import com.famlog.core.data.StartDestination
import com.famlog.core.data.StartResolver
import com.famlog.core.designsystem.UiText
import com.famlog.core.model.ApiError
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.SocialProvider
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AuthViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val context = mockk<Context>()
    private val social = mockk<SocialLoginClient>()
    private val auth = mockk<AuthRepository>()
    private val resolver = mockk<StartResolver>()

    /** 2026-11-17 서울 */
    private val clock = Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val viewModel = AuthViewModel(social, auth, resolver, clock)

    @Test
    fun 가입된_계정은_시작_분기로_이동한다() = runTest {
        coEvery { social.requestToken(SocialProvider.KAKAO, context) } returns SocialLoginResult.Token("t")
        coEvery { auth.login(SocialProvider.KAKAO, "t") } returns ApiResult.Success(Unit)
        coEvery { resolver.resolve() } returns StartDestination.Main

        viewModel.destinations.test {
            viewModel.login(SocialProvider.KAKAO, context)
            assertEquals(StartDestination.Main, awaitItem())
        }
        assertNull(viewModel.state.value.loadingProvider)
    }

    @Test
    fun 처음_온_계정은_가입_단계로_넘어가고_같은_소셜_토큰으로_가입한다() = runTest {
        coEvery { social.requestToken(SocialProvider.GOOGLE, context) } returns SocialLoginResult.Token("id-token")
        coEvery { auth.login(SocialProvider.GOOGLE, "id-token") } returns notRegistered
        coEvery { auth.signup(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { resolver.resolve() } returns StartDestination.GroupStart

        viewModel.login(SocialProvider.GOOGLE, context)
        assertEquals(AuthStep.SIGNUP, viewModel.state.value.step)

        viewModel.setAgreeAll(true)
        viewModel.onNicknameChange("  윤서 ")
        viewModel.onBirthYmChange("1990-03")
        viewModel.destinations.test {
            viewModel.signup()
            assertEquals(StartDestination.GroupStart, awaitItem())
        }
        coVerify { auth.signup(SocialProvider.GOOGLE, "id-token", "윤서", "199003") }
    }

    @Test
    fun 취소하면_안내_없이_원래대로() = runTest {
        coEvery { social.requestToken(any(), any()) } returns SocialLoginResult.Cancelled

        viewModel.login(SocialProvider.KAKAO, context)

        assertNull(viewModel.state.value.loadingProvider)
        assertNull(viewModel.state.value.message)
        coVerify(exactly = 0) { auth.login(any(), any()) }
    }

    @Test
    fun 서버_오류는_서버_안내를_보인다() = runTest {
        coEvery { social.requestToken(any(), any()) } returns SocialLoginResult.Token("t")
        coEvery { auth.login(any(), any()) } returns
            ApiResult.Failure(400, ApiError("VALIDATION_FAILED", "로그인 정보를 확인해 주세요.", null))

        viewModel.login(SocialProvider.KAKAO, context)

        assertEquals(UiText.Plain("로그인 정보를 확인해 주세요."), viewModel.state.value.message)
        assertEquals(AuthStep.LOGIN, viewModel.state.value.step)
    }

    @Test
    fun 키가_없는_빌드는_설정_안내() = runTest {
        coEvery { social.requestToken(any(), any()) } returns SocialLoginResult.NotConfigured

        viewModel.login(SocialProvider.GOOGLE, context)

        assertEquals(UiText.Res(R.string.login_error_not_configured), viewModel.state.value.message)
    }

    @Test
    fun 출생연월_검사_만_14세_경계() {
        viewModel.onBirthYmChange("2012")
        assertEquals(BirthCheck.INVALID, viewModel.state.value.birthCheck)
        viewModel.onBirthYmChange("201211") // 14번째 생일이 있는 달
        assertEquals(BirthCheck.UNDER_AGE, viewModel.state.value.birthCheck)
        viewModel.onBirthYmChange("201210")
        assertEquals(BirthCheck.OK, viewModel.state.value.birthCheck)
        viewModel.onBirthYmChange("")
        assertEquals(BirthCheck.EMPTY, viewModel.state.value.birthCheck)
    }

    @Test
    fun 필수_동의_둘과_닉네임_출생연월이_모두_있어야_가입할_수_있다() = runTest {
        enterSignup()
        viewModel.onNicknameChange("윤서")
        viewModel.onBirthYmChange("199003")
        assertFalse(viewModel.state.value.canSignup)

        viewModel.setAgreeTerms(true)
        assertFalse(viewModel.state.value.canSignup)
        viewModel.setAgreePrivacy(true)
        assertTrue(viewModel.state.value.canSignup)

        viewModel.onNicknameChange("   ")
        assertFalse(viewModel.state.value.canSignup)
    }

    @Test
    fun 만_14세_미만이면_가입_요청을_보내지_않는다() = runTest {
        enterSignup()
        viewModel.setAgreeAll(true)
        viewModel.onNicknameChange("윤서")
        viewModel.onBirthYmChange("201301")

        viewModel.signup()

        assertEquals(BirthCheck.UNDER_AGE, viewModel.state.value.birthCheck)
        coVerify(exactly = 0) { auth.signup(any(), any(), any(), any()) }
    }

    @Test
    fun 서버가_AGE_RESTRICTED면_출생연월_안내로_막는다() = runTest {
        enterSignup()
        viewModel.setAgreeAll(true)
        viewModel.onNicknameChange("윤서")
        viewModel.onBirthYmChange("201210")
        coEvery { auth.signup(any(), any(), any(), any()) } returns
            ApiResult.Failure(403, ApiError(ErrorCodes.AGE_RESTRICTED, "만 14세 이상만 가입할 수 있어요.", null))

        viewModel.signup()

        assertEquals(BirthCheck.UNDER_AGE, viewModel.state.value.birthCheck)
        assertFalse(viewModel.state.value.canSignup)
        assertFalse(viewModel.state.value.submitting)
    }

    @Test
    fun 가입_단계에서_뒤로가면_입력을_지우고_로그인으로() = runTest {
        enterSignup()
        viewModel.onNicknameChange("윤서")

        viewModel.backToLogin()

        assertEquals(AuthUiState(), viewModel.state.value)
    }

    private fun enterSignup() {
        coEvery { social.requestToken(any(), any()) } returns SocialLoginResult.Token("t")
        coEvery { auth.login(any(), any()) } returns notRegistered
        viewModel.login(SocialProvider.KAKAO, context)
        assertEquals(AuthStep.SIGNUP, viewModel.state.value.step)
    }

    private val notRegistered =
        ApiResult.Failure(404, ApiError(ErrorCodes.USER_NOT_REGISTERED, "처음 오셨네요.", null))
}
