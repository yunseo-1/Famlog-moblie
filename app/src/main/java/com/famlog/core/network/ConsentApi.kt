package com.famlog.core.network

import com.famlog.core.model.Consent
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** 내 동의 (사양서 8.2·11.3). 지금 바꿀 수 있는 것은 LOCATION뿐이고 나머지는 400 */
interface ConsentApi {
    /** 종류 5개 모두 */
    @GET("users/me/consents")
    suspend fun list(): ConsentListResponse

    /** 동의 {agreed:true, docVersion}(서버 현재 버전과 다르면 400), 철회 {agreed:false}. 여러 번 보내도 같다 */
    @PUT("users/me/consents/{type}")
    suspend fun update(@Path("type") type: String, @Body body: UpdateConsentRequest): ConsentResponse
}

@Serializable
data class UpdateConsentRequest(val agreed: Boolean, val docVersion: String? = null)

@Serializable
data class ConsentResponse(
    val type: String,
    val agreed: Boolean = false,
    val docVersion: String? = null,
    val currentVersion: String? = null,
    val reconsentRequired: Boolean = false,
)

@Serializable
data class ConsentListResponse(val items: List<ConsentResponse>)

fun ConsentResponse.toModel() = Consent(type, agreed, docVersion, currentVersion, reconsentRequired)
