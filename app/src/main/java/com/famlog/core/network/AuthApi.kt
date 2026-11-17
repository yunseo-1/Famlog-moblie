package com.famlog.core.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/** 토큰 재발급 전용. OkHttp Authenticator 안에서 동기로 부르므로 Call을 돌려준다 */
interface AuthApi {
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<TokenResponse>
}
