package com.famlog.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 신고 (MOD-01). 처리 전에 같은 대상을 다시 신고하면 같은 신고를 돌려준다(201, 같은 id).
 * 볼 수 없는 대상은 404, 구성원이 아닌 그룹은 403. 처리 결과 알림은 없다
 */
interface ReportApi {
    @POST("reports")
    suspend fun create(@Body body: ReportRequest): ReportResponse
}

@Serializable
data class ReportRequest(
    val targetType: String,
    val targetId: Long,
    val groupId: Long? = null,
    val reason: String,
    val detail: String? = null,
)

@Serializable
data class ReportResponse(val id: Long, val targetType: String, val targetId: Long, val reason: String, val status: String)
