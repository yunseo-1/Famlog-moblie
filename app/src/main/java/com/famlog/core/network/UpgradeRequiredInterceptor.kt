package com.famlog.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** 최소 지원 버전보다 낮으면 서버가 모든 API에 426 UPGRADE_REQUIRED → 강제 업데이트 화면(SCR-00)으로 보낸다 */
class UpgradeRequiredInterceptor(private val sessionEvents: SessionEvents) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == HTTP_UPGRADE_REQUIRED) sessionEvents.notifyUpgradeRequired()
        return response
    }

    private companion object {
        const val HTTP_UPGRADE_REQUIRED = 426
    }
}
