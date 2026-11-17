package com.famlog.core.data

import com.famlog.core.datastore.SessionStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.FamilyRole
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.Invite
import com.famlog.core.model.Member
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.CreateGroupRequest
import com.famlog.core.network.CurrentGroupRequest
import com.famlog.core.network.GroupApi
import com.famlog.core.network.JoinRequest
import com.famlog.core.network.UpdateMyMemberRequest
import com.famlog.core.network.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 내 가족 그룹 목록과 현재 그룹 (GRP-01~04).
 * 현재 그룹 ID는 [SessionStore]에 저장해 앱을 다시 켜도 유지하고, 사용자가 바꾸면 서버에도 알린다 (GRP-03).
 */
@Singleton
class GroupRepository @Inject constructor(
    private val api: GroupApi,
    private val caller: ApiCaller,
    private val sessionStore: SessionStore,
) {
    private val _groups = MutableStateFlow<List<GroupSummary>>(emptyList())
    val groups: StateFlow<List<GroupSummary>> = _groups.asStateFlow()

    val currentGroupId: Flow<Long?> = sessionStore.currentGroupId

    val currentGroup: Flow<GroupSummary?> =
        combine(_groups, sessionStore.currentGroupId) { groups, id -> groups.firstOrNull { it.id == id } }

    suspend fun refreshGroups(): ApiResult<List<GroupSummary>> =
        caller.call { api.myGroups() }
            .map { list -> list.map { it.toModel() } }
            .also { if (it is ApiResult.Success) _groups.value = it.value }

    suspend fun detail(groupId: Long): ApiResult<GroupDetail> =
        caller.call { api.detail(groupId) }.map { it.toModel() }

    /** 만든 그룹이 현재 그룹이 된다 (서버도 같은 처리) */
    suspend fun create(name: String): ApiResult<GroupDetail> =
        caller.call { api.create(CreateGroupRequest(name)) }.map { it.toModel() }.also { enter(it) }

    /** 참여한 그룹이 현재 그룹이 된다. 코드의 공백·하이픈·대소문자는 서버가 무시한다 */
    suspend fun join(code: String): ApiResult<GroupDetail> =
        caller.call { api.join(JoinRequest(code)) }.map { it.toModel() }.also { enter(it) }

    /** 그룹 전환 (SCR-70). 서버에 먼저 알리고 성공하면 로컬에도 저장한다 */
    suspend fun switchTo(groupId: Long): ApiResult<Unit> =
        caller.call { api.changeCurrentGroup(CurrentGroupRequest(groupId)) }
            .also { if (it is ApiResult.Success) sessionStore.saveCurrentGroup(groupId) }

    /** 앱 시작 시 저장된 현재 그룹이 없거나 더는 구성원이 아닐 때 다른 그룹을 고른다 (서버는 바꾸지 않는다) */
    suspend fun selectLocally(groupId: Long?) = sessionStore.saveCurrentGroup(groupId)

    suspend fun updateMyMember(
        groupId: Long,
        familyRole: FamilyRole,
        familyTitle: String?,
        calendarColor: String?,
    ): ApiResult<Member> = caller.call {
        api.updateMe(groupId, UpdateMyMemberRequest(familyRole.name, familyTitle, calendarColor))
    }.map { it.toModel() }

    suspend fun issueInvite(groupId: Long): ApiResult<Invite> =
        caller.call { api.issueInvite(groupId) }.map { it.toModel() }

    /** 그룹 상세에서 내 구성원 정보. 로그인 정보가 없으면 null */
    suspend fun me(detail: GroupDetail): Member? = sessionStore.userId()?.let(detail::member)

    private suspend fun enter(result: ApiResult<GroupDetail>) {
        if (result !is ApiResult.Success) return
        val group = result.value
        _groups.update { list -> list.filterNot { it.id == group.id } + group.toSummary() }
        sessionStore.saveCurrentGroup(group.id)
    }
}
