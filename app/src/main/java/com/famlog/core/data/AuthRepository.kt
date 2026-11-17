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
import com.famlog.core.network.DeviceRequest
import com.famlog.core.network.LoginRequest
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
    @DeviceName private val deviceName: String,
) {

    suspend fun login(provider: SocialProvider, providerToken: String): ApiResult<Unit> {
        val request = LoginRequest(providerToken, device())
        return save(caller.call { api.login(provider.path, request) })
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
    }

    companion object {
        /** 서버 famlog.consent.terms-version / privacy-version과 같아야 가입된다. 약관 전문은 S22 */
        const val TERMS_VERSION = "1.0"
        const val PRIVACY_VERSION = "1.0"
    }
}
