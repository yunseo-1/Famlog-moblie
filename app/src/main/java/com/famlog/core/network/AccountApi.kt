package com.famlog.core.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** 소셜 로그인·가입 (AUTH-01, AUTH-02). 가입 전 계정은 로그인이 404 USER_NOT_REGISTERED */
interface AccountApi {
    @POST("auth/{provider}/login")
    suspend fun login(@Path("provider") provider: String, @Body body: LoginRequest): AuthResponse

    @POST("auth/{provider}/signup")
    suspend fun signup(@Path("provider") provider: String, @Body body: SignupRequest): AuthResponse
}
