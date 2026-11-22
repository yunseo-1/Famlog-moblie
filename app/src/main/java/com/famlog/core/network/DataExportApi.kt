package com.famlog.core.network

import com.famlog.core.model.DataExport
import com.famlog.core.model.DataExportStatus
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 내 데이터 내보내기 (ACC-02). 요청하면 서버 배치가 ZIP을 만들고(보통 1~2분) 푸시 DATA_EXPORT_READY·STOMP data-export.ready로 알린다.
 * 진행 중인 요청이 있으면 그 요청을 돌려주고, 준비된 지 24시간 안이면 429 RATE_LIMITED. FAILED는 제한에 세지 않는다
 */
interface DataExportApi {
    @POST("users/me/data-exports")
    suspend fun request(): DataExportResponse

    /** 최근 5개, 최신 순. downloadUrl은 늘 null */
    @GET("users/me/data-exports")
    suspend fun list(): DataExportListResponse

    /** READY면 10분짜리 downloadUrl(조회할 때마다 새로). 다른 사람의 id면 404 */
    @GET("users/me/data-exports/{exportId}")
    suspend fun detail(@Path("exportId") exportId: Long): DataExportResponse
}

@Serializable
data class DataExportResponse(
    val id: Long,
    val status: String,
    val requestedAt: String,
    val readyAt: String? = null,
    val expiresAt: String? = null,
    val sizeBytes: Long? = null,
    val downloadUrl: String? = null,
    val fileName: String? = null,
) {
    override fun toString(): String = "DataExportResponse(id=$id, status=$status)"
}

@Serializable
data class DataExportListResponse(val items: List<DataExportResponse> = emptyList())

fun DataExportResponse.toModel() = DataExport(
    id = id,
    status = DataExportStatus.of(status),
    requestedAt = parseServerTime(requestedAt),
    readyAt = readyAt?.let(::parseServerTime),
    expiresAt = expiresAt?.let(::parseServerTime),
    sizeBytes = sizeBytes,
    downloadUrl = downloadUrl?.takeIf { it.isNotBlank() },
    fileName = fileName?.takeIf { it.isNotBlank() },
)
