package com.famlog.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** 일정·공휴일 (SCH-01~05). 다른 그룹 일정 id는 404, 수정·삭제 권한이 없으면 403 GROUP_ROLE_REQUIRED */
interface ScheduleApi {
    /** [from]·[to]는 서울 날짜(포함), 1~100일. 반복 일정은 회차마다 한 줄 */
    @GET("groups/{groupId}/schedules")
    suspend fun occurrences(
        @Path("groupId") groupId: Long,
        @Query("from") from: String,
        @Query("to") to: String,
    ): OccurrenceListResponse

    @POST("groups/{groupId}/schedules")
    suspend fun create(@Path("groupId") groupId: Long, @Body body: ScheduleRequest): ScheduleResponse

    @GET("schedules/{scheduleId}")
    suspend fun detail(@Path("scheduleId") scheduleId: Long): ScheduleResponse

    /** 값 전체 + 조회한 version. 다르면 409 VERSION_CONFLICT. 반복 일정은 시리즈 전체가 바뀐다 */
    @PATCH("schedules/{scheduleId}")
    suspend fun update(@Path("scheduleId") scheduleId: Long, @Body body: ScheduleRequest): ScheduleResponse

    @DELETE("schedules/{scheduleId}")
    suspend fun delete(@Path("scheduleId") scheduleId: Long)

    /** 시각의 `+09:00`은 Retrofit이 `%2B09:00`으로 인코딩한다 (ScheduleRepositoryTest) */
    @GET("groups/{groupId}/schedules/conflicts")
    suspend fun conflicts(
        @Path("groupId") groupId: Long,
        @Query("startsAt") startsAt: String,
        @Query("endsAt") endsAt: String,
        @Query("memberIds") memberIds: List<Long>,
        @Query("excludeId") excludeId: Long?,
    ): ConflictListResponse

    @GET("holidays")
    suspend fun holidays(@Query("year") year: Int): HolidayListResponse
}
