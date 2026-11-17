package com.famlog.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.famlog.BuildConfig
import com.famlog.core.model.SocialProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface SocialLoginResult {
    /** 서버에 보낼 토큰 (카카오: 액세스 토큰, Google: ID 토큰). 로그에 남기지 않는다 */
    data class Token(val value: String) : SocialLoginResult {
        override fun toString(): String = "Token(***)"
    }

    data object Cancelled : SocialLoginResult

    /** local.properties에 앱 키가 없다 (CI 빌드 등) */
    data object NotConfigured : SocialLoginResult
    data object Failed : SocialLoginResult
}

/** 카카오·Google SDK로 소셜 토큰을 받는다. 로그인 창을 띄우므로 Activity Context가 필요하다 */
interface SocialLoginClient {
    suspend fun requestToken(provider: SocialProvider, activityContext: Context): SocialLoginResult
}

@Singleton
class SdkSocialLoginClient @Inject constructor() : SocialLoginClient {

    override suspend fun requestToken(provider: SocialProvider, activityContext: Context): SocialLoginResult =
        when (provider) {
            SocialProvider.KAKAO -> kakao(activityContext)
            SocialProvider.GOOGLE -> google(activityContext)
        }

    /** 카카오톡이 있으면 카카오톡으로, 없거나 실패하면 카카오계정(웹)으로 로그인한다 (카카오 SDK 권장 순서) */
    private suspend fun kakao(context: Context): SocialLoginResult {
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) return SocialLoginResult.NotConfigured
        val client = UserApiClient.instance
        if (client.isKakaoTalkLoginAvailable(context)) {
            try {
                return SocialLoginResult.Token(client.awaitLogin { loginWithKakaoTalk(context, callback = it) }.accessToken)
            } catch (e: ClientError) {
                if (e.reason == ClientErrorCause.Cancelled) return SocialLoginResult.Cancelled
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 카카오톡에 연결된 계정이 없는 경우 등: 카카오계정 로그인으로 넘어간다
            }
        }
        return try {
            SocialLoginResult.Token(client.awaitLogin { loginWithKakaoAccount(context, callback = it) }.accessToken)
        } catch (e: ClientError) {
            if (e.reason == ClientErrorCause.Cancelled) SocialLoginResult.Cancelled else SocialLoginResult.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SocialLoginResult.Failed
        }
    }

    private suspend fun UserApiClient.awaitLogin(
        start: UserApiClient.((OAuthToken?, Throwable?) -> Unit) -> Unit,
    ): OAuthToken = suspendCancellableCoroutine { cont ->
        start { token, error ->
            when {
                error != null -> cont.resumeWithException(error)
                token != null -> cont.resume(token)
                else -> cont.resumeWithException(IllegalStateException("카카오 로그인 결과 없음"))
            }
        }
    }

    /** Credential Manager의 "Google로 로그인" 버튼 흐름. ID 토큰의 aud는 백엔드 웹 클라이언트 ID */
    private suspend fun google(context: Context): SocialLoginResult {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return SocialLoginResult.NotConfigured
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                SocialLoginResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                SocialLoginResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            SocialLoginResult.Cancelled
        } catch (e: GetCredentialException) {
            SocialLoginResult.Failed
        } catch (e: GoogleIdTokenParsingException) {
            SocialLoginResult.Failed
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialLoginModule {
    @Binds
    abstract fun bindSocialLoginClient(impl: SdkSocialLoginClient): SocialLoginClient
}
