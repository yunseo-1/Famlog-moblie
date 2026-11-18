package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.Holiday
import com.famlog.core.model.Occurrence
import com.famlog.core.model.Schedule
import com.famlog.core.model.ScheduleConflict
import com.famlog.core.model.ScheduleDraft
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.ScheduleApi
import com.famlog.core.network.toModel
import com.famlog.core.network.toRequest
import com.famlog.core.network.toServerTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 일정·공휴일 (SCH-01~05). 일정은 서버가 원본이고 화면에 들어올 때마다 받는다(로컬 캐시 없음).
 * 등록·수정·삭제가 끝나면 [changes]로 알려 캘린더가 다시 받게 한다.
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val api: ScheduleApi,
    private val caller: ApiCaller,
) {
    private val _changes = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** 바뀐 일정의 그룹 ID */
    val changes: SharedFlow<Long> = _changes.asSharedFlow()

    /** 공휴일은 해마다 한 번만 받는다. 빈 목록(데이터 없는 해)도 저장한다 */
    private val holidayCache = mutableMapOf<Int, List<Holiday>>()
    private val holidayLock = Mutex()

    suspend fun occurrences(groupId: Long, from: LocalDate, to: LocalDate): ApiResult<List<Occurrence>> =
        caller.call { api.occurrences(groupId, from.toString(), to.toString()) }
            .map { response -> response.items.map { it.toModel() } }

    suspend fun detail(scheduleId: Long): ApiResult<Schedule> = caller.call { api.detail(scheduleId).toModel() }

    suspend fun create(groupId: Long, draft: ScheduleDraft): ApiResult<Schedule> =
        caller.call { api.create(groupId, draft.toRequest()).toModel() }.also { notifyIfSuccess(it, groupId) }

    /** 다른 사람이 먼저 고쳤으면 409 VERSION_CONFLICT */
    suspend fun update(schedule: Schedule, draft: ScheduleDraft): ApiResult<Schedule> =
        caller.call { api.update(schedule.id, draft.toRequest(schedule.version)).toModel() }
            .also { notifyIfSuccess(it, schedule.groupId) }

    suspend fun delete(schedule: Schedule): ApiResult<Unit> =
        caller.call { api.delete(schedule.id) }.also { notifyIfSuccess(it, schedule.groupId) }

    /** 수정 중이면 [excludeId]에 그 일정 id (자기 자신과 겹친다고 하지 않게) */
    suspend fun conflicts(
        groupId: Long,
        startsAt: Instant,
        endsAt: Instant,
        memberIds: List<Long>,
        excludeId: Long?,
    ): ApiResult<List<ScheduleConflict>> = caller.call {
        api.conflicts(groupId, startsAt.toServerTime(), endsAt.toServerTime(), memberIds, excludeId)
    }.map { response -> response.items.map { it.toModel() } }

    suspend fun holidays(year: Int): ApiResult<List<Holiday>> = holidayLock.withLock {
        holidayCache[year]?.let { return ApiResult.Success(it) }
        caller.call { api.holidays(year) }
            .map { response -> response.items.map { it.toModel() } }
            .also { if (it is ApiResult.Success) holidayCache[year] = it.value }
    }

    private fun notifyIfSuccess(result: ApiResult<*>, groupId: Long) {
        if (result is ApiResult.Success) _changes.tryEmit(groupId)
    }
}
