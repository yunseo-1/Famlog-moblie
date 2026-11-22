package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.DataExport
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.DataExportApi
import com.famlog.core.network.toModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 내 데이터 내보내기 (ACC-02). 상태는 서버가 기준이라 기기에 저장하지 않는다 */
@Singleton
class DataExportRepository @Inject constructor(
    private val api: DataExportApi,
    private val caller: ApiCaller,
) {
    private val _ready = MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 준비됐다는 실시간 알림(STOMP data-export.ready)의 요청 ID */
    val ready: SharedFlow<Long> = _ready.asSharedFlow()

    /** 가장 최근 요청 (없으면 null). 화면에 들어올 때 상태를 되살린다 */
    suspend fun latest(): ApiResult<DataExport?> = caller.call { api.list() }.map { it.items.firstOrNull()?.toModel() }

    /** 진행 중이면 그 요청이 돌아온다. 하루에 한 번(429 RATE_LIMITED, 서버 문구) */
    suspend fun request(): ApiResult<DataExport> = caller.call { api.request().toModel() }

    /** READY면 새 10분짜리 링크가 들어 있다. 내려받기 직전에 부른다 */
    suspend fun detail(id: Long): ApiResult<DataExport> = caller.call { api.detail(id).toModel() }

    fun onReady(id: Long) {
        _ready.tryEmit(id)
    }
}
