package com.famlog.core.data

import com.famlog.core.datastore.SessionStore
import com.famlog.core.datastore.TokenStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupDetail
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 로그인 상태와 그룹 상태로 정한 다음 화면 */
sealed interface StartDestination {
    data object Login : StartDestination
    data object GroupStart : StartDestination
    data class RoleSetup(val groupId: Long) : StartDestination
    data object Main : StartDestination

    /** 서버에 닿지 못했다. 다시 시도를 보여준다 */
    data object Unavailable : StartDestination

    /** 최소 지원 버전보다 낮다 → 강제 업데이트 (SCR-00) */
    data object UpdateRequired : StartDestination
}

/**
 * 앱 시작·로그인 직후 어디로 갈지 정한다 (18.1 AUTH-01).
 * 토큰 없음 → 로그인 / 그룹 없음 → SCR-02 / 현재 그룹에서 내 가족 역할이 없음 → SCR-03 / 그 외 → 탭 화면.
 */
class StartResolver @Inject constructor(
    private val tokenStore: TokenStore,
    private val sessionStore: SessionStore,
    private val groupRepository: GroupRepository,
    private val appConfigRepository: AppConfigRepository,
) {

    suspend fun resolve(): StartDestination {
        // 앱 설정을 못 받아도 막지 않는다 (낮은 버전이면 다른 API가 426으로 다시 알린다)
        appConfigRepository.refresh()
        if (appConfigRepository.updateRequired()) return StartDestination.UpdateRequired
        if (tokenStore.get() == null || sessionStore.userId() == null) {
            tokenStore.clear()
            return StartDestination.Login
        }
        val groups = when (val result = groupRepository.refreshGroups()) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return failureDestination(result)
            ApiResult.NetworkError -> return StartDestination.Unavailable
        }
        if (groups.isEmpty()) {
            groupRepository.selectLocally(null)
            return StartDestination.GroupStart
        }
        val savedId = groupRepository.currentGroupId.first()
        val group = groups.firstOrNull { it.id == savedId } ?: groups.first()
        if (group.id != savedId) groupRepository.selectLocally(group.id)
        return enter(group.id)
    }

    /** 그룹에 들어갈 때(시작·전환) 내 가족 역할이 있는지 확인한다 */
    suspend fun enter(groupId: Long): StartDestination = when (val result = groupRepository.detail(groupId)) {
        is ApiResult.Success -> afterJoin(result.value)
        is ApiResult.Failure -> failureDestination(result)
        ApiResult.NetworkError -> StartDestination.Unavailable
    }

    /** 만들기·참여 응답처럼 그룹 상세를 이미 받았을 때 */
    suspend fun afterJoin(detail: GroupDetail): StartDestination =
        if (groupRepository.me(detail)?.familyRole == null) {
            StartDestination.RoleSetup(detail.id)
        } else {
            StartDestination.Main
        }

    /** 401은 TokenAuthenticator가 이미 토큰을 지웠다(재발급 실패·무효) → 로그인 */
    private fun failureDestination(failure: ApiResult.Failure) = when (failure.status) {
        401 -> StartDestination.Login
        426 -> StartDestination.UpdateRequired
        else -> StartDestination.Unavailable
    }
}
