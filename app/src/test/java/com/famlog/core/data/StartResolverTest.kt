package com.famlog.core.data

import com.famlog.core.datastore.FakeTokenStore
import com.famlog.core.model.AuthTokens
import com.famlog.core.network.GroupApi
import com.famlog.testing.FakeSessionStore
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 앱 시작 분기 (18.1 AUTH-01) */
class StartResolverTest {

    private val server = MockWebServer()
    private val tokenStore = FakeTokenStore(AuthTokens("access", "refresh"))
    private val session = FakeSessionStore(userId = 7)
    private lateinit var resolver: StartResolver

    @Before
    fun setUp() {
        server.start()
        val groups = GroupRepository(server.api<GroupApi>(), testApiCaller(), session)
        resolver = StartResolver(tokenStore, session, groups)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 토큰이_없으면_로그인() = runTest {
        tokenStore.tokens = null

        assertEquals(StartDestination.Login, resolver.resolve())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun 사용자_ID가_없으면_토큰을_지우고_로그인() = runTest {
        session.userId = null

        assertEquals(StartDestination.Login, resolver.resolve())
        assertNull(tokenStore.tokens)
    }

    @Test
    fun 그룹이_없으면_그룹_시작() = runTest {
        session.saveCurrentGroup(3)
        server.enqueue(jsonResponse("[]"))

        assertEquals(StartDestination.GroupStart, resolver.resolve())
        assertNull(session.currentGroupIdValue)
    }

    @Test
    fun 저장된_현재_그룹으로_들어가고_역할이_있으면_탭_화면() = runTest {
        session.saveCurrentGroup(2)
        server.enqueue(jsonResponse(twoGroups))
        server.enqueue(jsonResponse(groupDetailJson(id = 2, familyRole = "DAUGHTER", name = "외가")))

        assertEquals(StartDestination.Main, resolver.resolve())
        server.takeRequest()
        assertEquals("/api/v1/groups/2", server.takeRequest().path)
        assertEquals(2L, session.currentGroupIdValue)
    }

    @Test
    fun 저장된_그룹에서_나갔으면_첫_그룹을_고르고_역할이_없으면_역할_설정() = runTest {
        session.saveCurrentGroup(99)
        server.enqueue(jsonResponse(twoGroups))
        server.enqueue(jsonResponse(groupDetailJson(id = 1, familyRole = null)))

        assertEquals(StartDestination.RoleSetup(1), resolver.resolve())
        assertEquals(1L, session.currentGroupIdValue)
    }

    @Test
    fun 인증_실패는_로그인_서버_오류는_다시_시도() = runTest {
        server.enqueue(errorResponse(401, "AUTH_INVALID"))
        assertEquals(StartDestination.Login, resolver.resolve())

        server.enqueue(errorResponse(500, "INTERNAL_ERROR"))
        assertEquals(StartDestination.Unavailable, resolver.resolve())
    }

    private val twoGroups = """[{"id":1,"name":"우리 가족","memberCount":4,"myRole":"OWNER"},
        {"id":2,"name":"외가","memberCount":6,"myRole":"MEMBER"}]"""
}
