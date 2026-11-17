package com.famlog.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 채팅 REST (CHAT-03·04·06). 메시지 보내기는 STOMP로만 한다 ([ChatSocket]) */
interface ChatApi {
    /** afterSeq: 그 다음부터 오래된 순, beforeSeq: 그 이전의 최신 size개, 둘 다 없으면 최신 size개. items는 언제나 seq 오름차순 */
    @GET("groups/{groupId}/messages")
    suspend fun messages(
        @Path("groupId") groupId: Long,
        @Query("afterSeq") afterSeq: Long? = null,
        @Query("beforeSeq") beforeSeq: Long? = null,
        @Query("size") size: Int? = null,
    ): MessagePage

    @PUT("groups/{groupId}/read")
    suspend fun read(@Path("groupId") groupId: Long, @Body body: ReadRequest): ReadResponse

    /** 보낸 사람만, 5분 안에. 지난 메시지는 403 MESSAGE_NOT_DELETABLE */
    @DELETE("messages/{messageId}")
    suspend fun delete(@Path("messageId") messageId: Long)

    /** 한 사람당 공감 1개. 다른 공감을 누르면 바뀐다 */
    @POST("messages/{messageId}/reactions")
    suspend fun react(@Path("messageId") messageId: Long, @Body body: ReactionRequest): ReactionsResponse

    @DELETE("messages/{messageId}/reactions/me")
    suspend fun unreact(@Path("messageId") messageId: Long): ReactionsResponse
}
