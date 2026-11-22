package com.famlog.core.data

import com.famlog.core.common.DeviceName
import com.famlog.core.database.MessageDao
import com.famlog.core.datastore.DeviceIdProvider
import com.famlog.core.datastore.SessionStore
import com.famlog.core.datastore.TokenStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.AuthTokens
import com.famlog.core.model.SocialProvider
import com.famlog.core.model.map
import com.famlog.core.network.AccountApi
import com.famlog.core.network.AgreementsRequest
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.AppHeadersInterceptor
import com.famlog.core.network.AuthResponse
import com.famlog.core.network.DeleteAccountRequest
import com.famlog.core.network.DeviceRequest
import com.famlog.core.network.LoginRequest
import com.famlog.core.network.RefreshRequest
import com.famlog.core.network.SessionEvents
import com.famlog.core.network.SignupRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 소셜 로그인·가입 (AUTH-01, AUTH-02). 성공하면 토큰과 사용자 ID·마지막 그룹을 저장한다.
 * 가입 전 계정이면 login이 Failure(404, USER_NOT_REGISTERED)를 돌려주고, 화면이 가입 단계로 넘어간다.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AccountApi,
    private val caller: ApiCaller,
    private val tokenStore: TokenStore,
    private val sessionStore: SessionStore,
    private val deviceIdProvider: DeviceIdProvider,
    private val messageDao: MessageDao,
    private val pushTokens: PushTokenRepository,
    private val sessionEvents: SessionEvents,
    private val appLock: AppLock,
    @DeviceName private val deviceName: String,
) {

    /** [restore]면 삭제 예정 계정을 되살리며 로그인한다 (403 ACCOUNT_PENDING_DELETION 다음에) */
    suspend fun login(provider: SocialProvider, providerToken: String, restore: Boolean = false): ApiResult<Unit> {
        val request = LoginRequest(providerToken, device(), restore = true.takeIf { restore })
        return save(caller.call { api.login(provider.path, request) })
    }

    /** 이 기기만 로그아웃. 서버에 닿지 못해도 기기에서는 로그아웃한다 */
    suspend fun logout() {
        tokenStore.get()?.let { tokens -> caller.call { api.logout(RefreshRequest(tokens.refreshToken)) } }
        signOutLocally()
    }

    /** 계정 삭제 (ACC-01). 성공하면 서버가 모든 기기를 로그아웃시키므로 이 기기도 정리한다 */
    suspend fun deleteAccount(deleteMedia: Boolean): ApiResult<Unit> =
        caller.call { api.deleteMe(DeleteAccountRequest(deleteMedia)) }.also { if (it is ApiResult.Success) signOutLocally() }

    /** 토큰·채팅 캐시를 지우고 로그인 화면으로 (채팅 연결·후보도 LoggedOut으로 정리된다) */
    private suspend fun signOutLocally() {
        tokenStore.clear()
        messageDao.clear()
        // 다음에 이 기기로 로그인하는 사람이 이전 잠금 설정에 묶이지 않게
        appLock.setEnabled(false)
        sessionEvents.notifyLoggedOut()
    }

    suspend fun signup(
        provider: SocialProvider,
        providerToken: String,
        nickname: String,
        birthYm: String,
    ): ApiResult<Unit> {
        val request = SignupRequest(
            providerToken = providerToken,
            device = device(),
            nickname = nickname,
            birthYm = birthYm,
            agreements = AgreementsRequest(TERMS_VERSION, PRIVACY_VERSION),
        )
        return save(caller.call { api.signup(provider.path, request) })
    }

    private suspend fun device() =
        DeviceRequest(deviceIdProvider.get(), deviceName, AppHeadersInterceptor.PLATFORM_ANDROID)

    private suspend fun save(result: ApiResult<AuthResponse>): ApiResult<Unit> = result.map { response ->
        // 다른 계정으로 들어왔으면 이전 계정의 채팅 캐시를 남기지 않는다
        if (sessionStore.userId() != response.user.id) messageDao.clear()
        tokenStore.save(AuthTokens(response.tokens.accessToken, response.tokens.refreshToken))
        sessionStore.saveUser(response.user.id, response.user.lastGroupId)
        // 이 기기(세션)로 푸시를 받게 토큰을 올린다. 실패해도 로그인은 그대로 (다음 시작 때 다시)
        pushTokens.registerInBackground()
    }

    companion object {
        /** 서버 famlog.consent.terms-version / privacy-version과 같아야 가입된다. 약관 전문은 S22 */
        const val TERMS_VERSION = "1.0"
        const val PRIVACY_VERSION = "1.0"
    }
}
