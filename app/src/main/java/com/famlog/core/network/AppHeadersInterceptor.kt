package com.famlog.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** 모든 요청에 앱 버전·플랫폼 헤더를 붙인다 (사양서 11.1). 재발급 클라이언트에도 쓴다 */
class AppHeadersInterceptor(private val appVersion: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header(HEADER_APP_VERSION, appVersion)
                .header(HEADER_PLATFORM, PLATFORM_ANDROID)
                .build(),
        )

    companion object {
        const val HEADER_APP_VERSION = "X-App-Version"
        const val HEADER_PLATFORM = "X-Platform"
        const val PLATFORM_ANDROID = "ANDROID"
    }
}
