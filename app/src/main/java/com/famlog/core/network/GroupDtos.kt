package com.famlog.core.network

import com.famlog.core.model.FamilyRole
import com.famlog.core.model.GroupDetail
import com.famlog.core.model.GroupRole
import com.famlog.core.model.GroupSummary
import com.famlog.core.model.Invite
import com.famlog.core.model.Member
import kotlinx.serialization.Serializable

/** 백엔드 GroupDtos와 같은 모양 (사양서 11.3 GRP-01~05). 역할 값은 모르는 값이 와도 깨지지 않게 문자열로 받는다 */
@Serializable
data class CreateGroupRequest(val name: String)

@Serializable
data class JoinRequest(val code: String)

/** 보낸 값만 바뀐다 (null은 보내지 않는다) */
@Serializable
data class UpdateMyMemberRequest(
    val familyRole: String? = null,
    val familyTitle: String? = null,
    val calendarColor: String? = null,
)

@Serializable
data class CurrentGroupRequest(val groupId: Long)

@Serializable
data class GroupSummaryResponse(
    val id: Long,
    val name: String,
    val imageKey: String? = null,
    val memberCount: Long,
    val myRole: String,
    val lastMessage: LastMessageResponse? = null,
    val unreadCount: Long = 0,
)

@Serializable
data class MemberResponse(
    val userId: Long,
    val nickname: String,
    val profileImageKey: String? = null,
    val role: String,
    val familyRole: String? = null,
    val familyTitle: String? = null,
    val calendarColor: String? = null,
)

@Serializable
data class GroupDetailResponse(
    val id: Long,
    val name: String,
    val imageKey: String? = null,
    val myRole: String,
    val members: List<MemberResponse>,
)

@Serializable
data class InviteResponse(
    val code: String,
    val link: String,
    val expiresAt: String,
)

private fun groupRoleOf(value: String): GroupRole =
    GroupRole.entries.firstOrNull { it.name == value } ?: GroupRole.MEMBER

private fun familyRoleOf(value: String?): FamilyRole? =
    value?.let { v -> FamilyRole.entries.firstOrNull { it.name == v } ?: FamilyRole.OTHER }

fun GroupSummaryResponse.toModel() =
    GroupSummary(id, name, memberCount.toInt(), groupRoleOf(myRole), lastMessage?.toModel(), unreadCount)

fun MemberResponse.toModel() = Member(
    userId = userId,
    nickname = nickname,
    role = groupRoleOf(role),
    familyRole = familyRoleOf(familyRole),
    familyTitle = familyTitle,
    calendarColor = calendarColor,
)

fun GroupDetailResponse.toModel() = GroupDetail(id, name, groupRoleOf(myRole), members.map { it.toModel() })

fun InviteResponse.toModel() = Invite(code, expiresAt)
