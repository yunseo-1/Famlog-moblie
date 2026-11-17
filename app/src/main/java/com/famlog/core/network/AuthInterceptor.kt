package com.famlog.core.network

import com.famlog.core.datastore.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** 저장된 Access 토큰이 있으면 Authorization 헤더를 붙인다. 토큰 값은 로그에 남기지 않는다 */
class AuthInterceptor @Inject constructor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER_AUTHORIZATION) != null) return chain.proceed(request)
        val accessToken = runBlocking { tokenStore.get() }?.accessToken
            ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(HEADER_AUTHORIZATION, bearer(accessToken)).build(),
        )
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "

        fun bearer(accessToken: String) = BEARER_PREFIX + accessToken

        fun accessTokenOf(headerValue: String?): String? = headerValue?.removePrefix(BEARER_PREFIX)
    }
}
