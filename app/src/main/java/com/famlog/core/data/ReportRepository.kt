package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.ReportReason
import com.famlog.core.model.ReportTarget
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.ReportApi
import com.famlog.core.network.ReportRequest
import javax.inject.Inject
import javax.inject.Singleton

/** 신고 (MOD-01). 신고 내용은 기기에 남기지 않는다 */
@Singleton
class ReportRepository @Inject constructor(
    private val api: ReportApi,
    private val caller: ApiCaller,
) {
    /** 접수된 신고 ID */
    suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?): ApiResult<Long> = caller.call {
        api.create(
            ReportRequest(
                targetType = target.type.name,
                targetId = target.id,
                groupId = target.groupId,
                reason = reason.name,
                detail = detail?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }.map { it.id }
}
