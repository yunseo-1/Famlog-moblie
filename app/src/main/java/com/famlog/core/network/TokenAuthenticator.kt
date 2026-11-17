package com.famlog.core.network

import com.famlog.core.datastore.TokenStore
import com.famlog.core.model.AuthTokens
import com.famlog.core.model.ErrorCodes
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 401 응답을 에러 코드로 나눠 처리한다.
 * - AUTH_TOKEN_EXPIRED: Refresh로 재발급 후 원래 요청을 한 번 다시 보낸다.
 * - AUTH_INVALID(또는 코드 없음): 토큰을 지우고 로그인 화면으로 보낸다.
 *
 * 서버는 이미 쓴 Refresh 토큰이 다시 오면 재사용으로 보고 모든 세션을 폐기한다 (사양서 9.2).
 * 그래서 재발급은 [lock]으로 한 번에 하나만 보내고, 기다리던 요청은 그 사이 바뀐 토큰을 그대로 쓴다.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    @RefreshClient private val authApi: AuthApi,
    private val errorParser: ApiErrorParser,
    private val sessionEvents: SessionEvents,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val sentAccessToken = AuthInterceptor.accessTokenOf(
            response.request.header(AuthInterceptor.HEADER_AUTHORIZATION),
        ) ?: return null // 인증 없이 보낸 요청(로그인 등)의 401은 호출한 쪽이 처리한다
        if (response.priorResponse != null) return null // 재발급 후 다시 보낸 요청도 401이면 더 시도하지 않는다

        if (errorParser.peek(response)?.code != ErrorCodes.AUTH_TOKEN_EXPIRED) {
            logout()
            return null
        }

        val tokens = synchronized(lock) { refreshIfNeeded(sentAccessToken) } ?: return null
        return response.request.newBuilder()
            .header(AuthInterceptor.HEADER_AUTHORIZATION, AuthInterceptor.bearer(tokens.accessToken))
            .build()
    }

    private fun refreshIfNeeded(sentAccessToken: String): AuthTokens? {
        val current = runBlocking { tokenStore.get() } ?: return null
        // 기다리는 동안 다른 요청이 이미 재발급했다
        if (current.accessToken != sentAccessToken) return current

        val result = try {
            authApi.refresh(RefreshRequest(current.refreshToken)).execute()
        } catch (e: IOException) {
            return null // 네트워크 오류: 토큰은 그대로 두고 이번 요청만 실패시킨다
        }
        val body = result.body()
        if (!result.isSuccessful || body == null) {
            logout()
            return null
        }
        val newTokens = AuthTokens(body.accessToken, body.refreshToken)
        runBlocking { tokenStore.save(newTokens) }
        return newTokens
    }

    private fun logout() {
        runBlocking { tokenStore.clear() }
        sessionEvents.notifyLoggedOut()
    }
}
