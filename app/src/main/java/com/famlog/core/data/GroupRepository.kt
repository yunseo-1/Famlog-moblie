package com.famlog.core.data

import com.famlog.core.datastore.SessionStore
import com.famlog.core.model.ApiResult
import com.famlog.core.model.FamilyRole
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.Invite
import com.famlog.core.model.LastMessage
import com.famlog.core.model.Member
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.ChangeRoleRequest
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
import kotlinx.coroutines.flow.first
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

    /** 그룹별 구성원 (채팅 보낸 사람 이름·서랍). [detail]을 부를 때마다 갱신한다 */
    private val _details = MutableStateFlow<Map<Long, GroupDetail>>(emptyMap())
    val details: StateFlow<Map<Long, GroupDetail>> = _details.asStateFlow()

    val currentGroupId: Flow<Long?> = sessionStore.currentGroupId

    val currentGroup: Flow<GroupSummary?> =
        combine(_groups, sessionStore.currentGroupId) { groups, id -> groups.firstOrNull { it.id == id } }

    suspend fun refreshGroups(): ApiResult<List<GroupSummary>> =
        caller.call { api.myGroups() }
            .map { list -> list.map { it.toModel() } }
            .also { if (it is ApiResult.Success) _groups.value = it.value }

    suspend fun detail(groupId: Long): ApiResult<GroupDetail> =
        caller.call { api.detail(groupId) }.map { it.toModel() }
            .also { if (it is ApiResult.Success) _details.update { map -> map + (groupId to it.value) } }

    /**
     * 실시간으로 받은 새 메시지를 채팅 탭 목록에 반영한다 (SCR-10).
     * 내가 보낸 것·시스템 메시지는 안 읽은 수에 넣지 않는다 (서버 countUnread와 같은 기준).
     */
    fun applyNewMessage(groupId: Long, message: LastMessage, countsAsUnread: Boolean) {
        _groups.update { list ->
            list.map { group ->
                if (group.id != groupId || (group.lastMessage?.seq ?: 0) > message.seq) return@map group
                group.copy(
                    lastMessage = message,
                    unreadCount = group.unreadCount + if (countsAsUnread) 1 else 0,
                )
            }
        }
    }

    fun applyDeleted(groupId: Long, seq: Long) {
        _groups.update { list ->
            list.map { group ->
                val last = group.lastMessage
                if (group.id == groupId && last?.seq == seq) group.copy(lastMessage = last.copy(deleted = true, body = null)) else group
            }
        }
    }

    /** 채팅방에서 끝까지 읽었다 */
    fun clearUnread(groupId: Long) {
        _groups.update { list -> list.map { if (it.id == groupId) it.copy(unreadCount = 0) else it } }
    }

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

    /** 권한 변경·그룹장 위임 (GRP-05). 성공하면 구성원 목록을 다시 받는다 (위임하면 내 역할도 바뀐다) */
    suspend fun changeRole(groupId: Long, userId: Long, role: GroupRole): ApiResult<Unit> =
        caller.call { api.changeRole(groupId, userId, ChangeRoleRequest(role.name)) }.map { }
            .also { if (it is ApiResult.Success) afterMembersChanged(groupId) }

    /** 내보내기 (GRP-05) */
    suspend fun removeMember(groupId: Long, userId: Long): ApiResult<Unit> =
        caller.call { api.removeMember(groupId, userId) }
            .also { if (it is ApiResult.Success) afterMembersChanged(groupId) }

    /** 나가기 (GRP-05). 성공하면 목록에서 빼고, 현재 그룹이었으면 비운다 (시작 분기가 다음 그룹을 고른다) */
    suspend fun leave(groupId: Long): ApiResult<Unit> = caller.call { api.leave(groupId) }.also { if (it is ApiResult.Success) forget(groupId) }

    /** 그룹 삭제 (그룹장) */
    suspend fun deleteGroup(groupId: Long): ApiResult<Unit> = caller.call { api.delete(groupId) }.also { if (it is ApiResult.Success) forget(groupId) }

    /** 그룹 상세에서 내 구성원 정보. 로그인 정보가 없으면 null */
    suspend fun me(detail: GroupDetail): Member? = sessionStore.userId()?.let(detail::member)

    private suspend fun afterMembersChanged(groupId: Long) {
        detail(groupId)
        refreshGroups()
    }

    private suspend fun forget(groupId: Long) {
        _groups.update { list -> list.filterNot { it.id == groupId } }
        _details.update { it - groupId }
        if (sessionStore.currentGroupId.first() == groupId) sessionStore.saveCurrentGroup(null)
    }

    private suspend fun enter(result: ApiResult<GroupDetail>) {
        if (result !is ApiResult.Success) return
        val group = result.value
        _groups.update { list -> list.filterNot { it.id == group.id } + group.toSummary() }
        sessionStore.saveCurrentGroup(group.id)
    }
}
