package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.FamilyRole
import com.famlog.core.model.GroupRole
import com.famlog.core.model.errorCode
import com.famlog.core.network.GroupApi
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 요청·응답 JSON이 백엔드 GroupDtos와 맞는지와 현재 그룹 저장을 확인한다 */
class GroupRepositoryTest {

    private val server = MockWebServer()
    private val session = FakeSessionStore(userId = 7, currentGroupId = null)
    private lateinit var repository: GroupRepository

    @Before
    fun setUp() {
        server.start()
        repository = GroupRepository(server.api<GroupApi>(), testApiCaller(), session)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 내_그룹_목록을_읽고_모르는_역할값은_MEMBER로_본다() = runTest {
        server.enqueue(
            jsonResponse(
                """[{"id":1,"name":"우리 가족","imageKey":null,"memberCount":4,"myRole":"OWNER","extra":1},
                   {"id":2,"name":"외가","memberCount":6,"myRole":"FUTURE_ROLE"}]""",
            ),
        )

        val result = repository.refreshGroups() as ApiResult.Success

        assertEquals("/api/v1/groups/me", server.takeRequest().path)
        assertEquals(listOf(GroupRole.OWNER, GroupRole.MEMBER), result.value.map { it.myRole })
        assertEquals(4, repository.groups.value.first().memberCount)
    }

    @Test
    fun 만들기에_성공하면_그_그룹이_현재_그룹이_된다() = runTest {
        server.enqueue(jsonResponse(groupDetailJson(id = 10, familyRole = null), code = 201))

        val result = repository.create("우리 가족") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"name":"우리 가족"}""", request.body.readUtf8())
        assertEquals(10L, session.currentGroupIdValue)
        assertEquals("우리 가족", repository.currentGroup.first()?.name)
        assertNull(repository.me(result.value)?.familyRole)
    }

    @Test
    fun 참여_실패는_서버_에러_코드를_그대로_돌려주고_현재_그룹을_바꾸지_않는다() = runTest {
        session.saveCurrentGroup(1)
        server.enqueue(errorResponse(410, ErrorCodes.INVITE_EXPIRED))

        val result = repository.join("k7mp-q2xa")

        assertEquals("""{"code":"k7mp-q2xa"}""", server.takeRequest().body.readUtf8())
        assertEquals(ErrorCodes.INVITE_EXPIRED, result.errorCode)
        assertEquals(410, (result as ApiResult.Failure).status)
        assertEquals(1L, session.currentGroupIdValue)
    }

    @Test
    fun 전환은_서버에_알린_뒤_로컬에_저장한다() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.switchTo(2)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/users/me/current-group", request.path)
        assertEquals("""{"groupId":2}""", request.body.readUtf8())
        assertTrue(result is ApiResult.Success)
        assertEquals(2L, session.currentGroupIdValue)
    }

    @Test
    fun 전환이_실패하면_현재_그룹을_유지한다() = runTest {
        session.saveCurrentGroup(1)
        server.enqueue(errorResponse(403, "FORBIDDEN"))

        repository.switchTo(2)

        assertEquals(1L, session.currentGroupIdValue)
    }

    @Test
    fun 내_역할_저장은_보낸_값만_담는다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"userId":7,"nickname":"윤서","role":"MEMBER","familyRole":"DAUGHTER","calendarColor":"#E8A33C"}""",
            ),
        )

        val result = repository.updateMyMember(10, FamilyRole.DAUGHTER, familyTitle = null, calendarColor = "#E8A33C")

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/groups/10/members/me", request.path)
        assertEquals("""{"familyRole":"DAUGHTER","calendarColor":"#E8A33C"}""", request.body.readUtf8())
        assertEquals(FamilyRole.DAUGHTER, (result as ApiResult.Success).value.familyRole)
    }

    @Test
    fun 연결_실패는_NetworkError() = runTest {
        server.shutdown()

        assertEquals(ApiResult.NetworkError, repository.refreshGroups())
    }

    // ---- 구성원 관리 (GRP-05) ----

    private val detailJson = """{"id":1,"name":"우리 가족","myRole":"OWNER","members":[
        {"userId":7,"nickname":"윤서","role":"ADMIN"},{"userId":8,"nickname":"엄마","role":"OWNER"}]}"""

    @Test
    fun 권한을_바꾸면_역할을_보내고_구성원과_그룹_목록을_다시_받는다() = runTest {
        server.enqueue(jsonResponse("""{"userId":8,"nickname":"엄마","role":"OWNER"}"""))
        server.enqueue(jsonResponse(detailJson))
        server.enqueue(jsonResponse("""[{"id":1,"name":"우리 가족","memberCount":2,"myRole":"ADMIN"}]"""))

        val result = repository.changeRole(1, 8, GroupRole.OWNER)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/groups/1/members/8", request.path)
        assertEquals("""{"role":"OWNER"}""", request.body.readUtf8())
        assertEquals("/api/v1/groups/1", server.takeRequest().path)
        assertEquals("/api/v1/groups/me", server.takeRequest().path)
        assertTrue(result is ApiResult.Success<*>)
        assertEquals(GroupRole.ADMIN, repository.groups.value.single().myRole)
    }

    @Test
    fun 내보내기는_DELETE로_보내고_실패하면_서버_오류() = runTest {
        server.enqueue(errorResponse(403, "GROUP_ROLE_REQUIRED", "그룹장은 내보낼 수 없어요."))

        val result = repository.removeMember(1, 8)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/groups/1/members/8", request.path)
        assertEquals(ErrorCodes.GROUP_ROLE_REQUIRED, result.errorCode)
    }

    @Test
    fun 나가거나_지우면_목록에서_빼고_현재_그룹이었으면_비운다() = runTest {
        server.enqueue(jsonResponse("""[{"id":1,"name":"우리 가족","memberCount":2,"myRole":"MEMBER"},{"id":2,"name":"외가","memberCount":1,"myRole":"OWNER"}]"""))
        repository.refreshGroups()
        session.saveCurrentGroup(1)
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.leave(1) is ApiResult.Success<*>)
        val leave = server.takeRequest()
        assertEquals("POST /api/v1/groups/1/leave", "${leave.method} ${leave.path}")
        assertEquals(listOf(2L), repository.groups.value.map { it.id })
        assertNull(session.currentGroupId.first())

        session.saveCurrentGroup(2)
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.deleteGroup(2) is ApiResult.Success<*>)
        val delete = server.takeRequest()
        assertEquals("DELETE /api/v1/groups/2", "${delete.method} ${delete.path}")
        assertTrue(repository.groups.value.isEmpty())
    }

    @Test
    fun 다른_가족이_있는_그룹장이_나가면_넘기라는_409() = runTest {
        server.enqueue(errorResponse(409, "OWNER_TRANSFER_REQUIRED", "그룹장을 넘긴 뒤 나갈 수 있어요."))

        assertEquals(ErrorCodes.OWNER_TRANSFER_REQUIRED, repository.leave(1).errorCode)
    }
}

fun groupDetailJson(id: Long, familyRole: String?, myUserId: Long = 7, name: String = "우리 가족"): String {
    val role = familyRole?.let { "\"$it\"" } ?: "null"
    return """{"id":$id,"name":"$name","imageKey":null,"myRole":"OWNER","members":[
        {"userId":$myUserId,"nickname":"윤서","profileImageKey":null,"role":"OWNER","familyRole":$role,
         "familyTitle":null,"calendarColor":"#14A3B8"},
        {"userId":99,"nickname":"아빠","role":"MEMBER","familyRole":"FATHER","calendarColor":"#E5568D"}]}"""
}
