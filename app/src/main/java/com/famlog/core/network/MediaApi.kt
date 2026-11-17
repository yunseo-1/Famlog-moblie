package com.famlog.core.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** 사진·동영상 업로드 (CHAT-05). 내려받기는 [MediaUrls]로 만든 주소를 이미지 로더·플레이어가 직접 부른다 */
interface MediaApi {
    /** 한 번에 30개. URL은 10분 유효 */
    @POST("media/upload-urls")
    suspend fun uploadUrls(@Body body: UploadUrlsRequest): UploadUrlsResponse

    /** 파일 앞부분으로 형식을 확인한다. 형식이 다르면 400 (파일은 지워진다). 다시 불러도 안전하다 */
    @POST("media/{mediaId}/complete")
    suspend fun complete(@Path("mediaId") mediaId: Long): MediaResponse
}
