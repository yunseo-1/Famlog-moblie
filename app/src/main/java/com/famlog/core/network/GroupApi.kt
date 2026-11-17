package com.famlog.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** 가족 그룹 (GRP-01~04) */
interface GroupApi {
    @GET("groups/me")
    suspend fun myGroups(): List<GroupSummaryResponse>

    @GET("groups/{groupId}")
    suspend fun detail(@Path("groupId") groupId: Long): GroupDetailResponse

    @POST("groups")
    suspend fun create(@Body body: CreateGroupRequest): GroupDetailResponse

    /** 없는 코드 404, 만료 410 INVITE_EXPIRED, 연속 5회 실패 시 10분 잠금 429. 이미 구성원이면 200 */
    @POST("groups/join")
    suspend fun join(@Body body: JoinRequest): GroupDetailResponse

    @PATCH("groups/{groupId}/members/me")
    suspend fun updateMe(@Path("groupId") groupId: Long, @Body body: UpdateMyMemberRequest): MemberResponse

    @POST("groups/{groupId}/invites")
    suspend fun issueInvite(@Path("groupId") groupId: Long): InviteResponse

    @PUT("users/me/current-group")
    suspend fun changeCurrentGroup(@Body body: CurrentGroupRequest)
}
