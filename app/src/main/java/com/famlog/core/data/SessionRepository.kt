package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.LoginSession
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.SessionApi
import com.famlog.core.network.toModel
import javax.inject.Inject
import javax.inject.Singleton

/** 로그인 기기 관리 (AUTH-03) */
@Singleton
class SessionRepository @Inject constructor(
    private val api: SessionApi,
    private val caller: ApiCaller,
) {
    /** 지금 기기가 맨 위, 나머지는 서버 순서(최근 사용 순) */
    suspend fun sessions(): ApiResult<List<LoginSession>> =
        caller.call { api.list() }.map { list -> list.map { it.toModel() }.sortedByDescending { it.current } }

    /** 다른 기기를 로그아웃시킨다. 그 기기는 다음 API 호출에서 로그인 화면으로 간다 */
    suspend fun revoke(id: Long): ApiResult<Unit> = caller.call { api.revoke(id) }
}
