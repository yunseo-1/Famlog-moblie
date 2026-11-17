package com.famlog.core.model

/** 그룹 권한 (사양서 2.2). 선언 순서가 권한 크기: MEMBER < ADMIN < OWNER */
enum class GroupRole {
    MEMBER,
    ADMIN,
    OWNER,
    ;

    /** 초대 코드 발급은 그룹장·관리자만 (GRP-02) */
    val canInvite: Boolean get() = this != MEMBER
}

/** 그룹 안에서의 가족 관계 (사양서 2.1). 같은 사람이 그룹마다 다를 수 있다 */
enum class FamilyRole { FATHER, MOTHER, SON, DAUGHTER, GRANDFATHER, GRANDMOTHER, OTHER }

enum class SocialProvider(val path: String) {
    KAKAO("kakao"),
    GOOGLE("google"),
}

/** [lastMessage]·[unreadCount]는 채팅 탭(SCR-10)용. 들어온 뒤 메시지가 없으면 [lastMessage]가 null */
data class GroupSummary(
    val id: Long,
    val name: String,
    val memberCount: Int,
    val myRole: GroupRole,
    val lastMessage: LastMessage? = null,
    val unreadCount: Long = 0,
)

/** [familyRole]이 null이면 아직 내 가족 역할을 정하지 않았다 → SCR-03 */
data class Member(
    val userId: Long,
    val nickname: String,
    val role: GroupRole,
    val familyRole: FamilyRole?,
    val familyTitle: String?,
    val calendarColor: String?,
) {
    /** 채팅·캘린더에 보일 이름: 호칭(예: 엄마)이 있으면 호칭, 없으면 닉네임 */
    val displayName: String get() = familyTitle?.takeIf { it.isNotBlank() } ?: nickname
}

data class GroupDetail(
    val id: Long,
    val name: String,
    val myRole: GroupRole,
    val members: List<Member>,
) {
    fun member(userId: Long): Member? = members.firstOrNull { it.userId == userId }

    fun toSummary() = GroupSummary(id, name, members.size, myRole)
}

data class Invite(val code: String, val expiresAt: String)
