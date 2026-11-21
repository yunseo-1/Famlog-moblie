package com.famlog.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** 가족 그룹 (GRP-01~05) */
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

    /**
     * 권한 변경·그룹장 위임 (관리자 이상, 자기 자신은 400). role=OWNER는 그룹장만 하고 이전 그룹장은 ADMIN이 된다.
     * 그룹장의 권한은 바꿀 수 없다(403), 삭제 예정 계정에는 위임할 수 없다(400)
     */
    @PATCH("groups/{groupId}/members/{userId}")
    suspend fun changeRole(@Path("groupId") groupId: Long, @Path("userId") userId: Long, @Body body: ChangeRoleRequest): MemberResponse

    /** 내보내기 (관리자 이상, 그룹장은 못 내보낸다). "○○님이 나갔어요" 시스템 메시지가 생긴다 */
    @DELETE("groups/{groupId}/members/{userId}")
    suspend fun removeMember(@Path("groupId") groupId: Long, @Path("userId") userId: Long)

    /** 나가기. 다른 구성원이 있는 그룹장은 409 OWNER_TRANSFER_REQUIRED, 혼자인 그룹장이 나가면 그룹이 삭제 예정 */
    @POST("groups/{groupId}/leave")
    suspend fun leave(@Path("groupId") groupId: Long)

    /** 그룹 삭제 (그룹장). 바로 접근이 막히고 30일 뒤 파기 */
    @DELETE("groups/{groupId}")
    suspend fun delete(@Path("groupId") groupId: Long)
}
