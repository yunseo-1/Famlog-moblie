package com.famlog.core.network

import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path

/** 소셜 로그인·가입 (AUTH-01, AUTH-02). 가입 전 계정은 로그인이 404 USER_NOT_REGISTERED */
interface AccountApi {
    @POST("auth/{provider}/login")
    suspend fun login(@Path("provider") provider: String, @Body body: LoginRequest): AuthResponse

    @POST("auth/{provider}/signup")
    suspend fun signup(@Path("provider") provider: String, @Body body: SignupRequest): AuthResponse

    /** 이 기기의 세션만 폐기한다. 모르는 토큰이어도 204 */
    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    /**
     * 계정 삭제 (ACC-01): 바로 모든 기기 로그아웃, 30일 뒤 파기. 다른 구성원이 있는 그룹의 그룹장이면 409 OWNER_TRANSFER_REQUIRED.
     * 유예 중 로그인은 403 ACCOUNT_PENDING_DELETION → 같은 소셜 토큰으로 restore=true 로그인
     */
    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteMe(@Body body: DeleteAccountRequest)
}
