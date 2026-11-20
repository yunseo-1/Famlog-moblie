package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.CandidateApproval
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateFields
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.CandidateSurface
import com.famlog.core.model.DismissReason
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.Reminder
import com.famlog.core.model.ScheduleDraft
import com.famlog.core.network.CandidateApi
import com.famlog.core.network.ChatEventEnvelope
import com.famlog.core.network.ChatEventTypes
import com.famlog.testing.api
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CandidateRepositoryTest {

    private val server = MockWebServer()
    private val clock = Clock.fixed(Instant.parse("2026-11-12T05:20:00Z"), ZoneId.of("Asia/Seoul"))
    private lateinit var repository: CandidateRepository

    @Before
    fun setUp() {
        server.start()
        repository = CandidateRepository(server.api<CandidateApi>(), testApiCaller(), testJson, clock)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 채팅방은_보이는_범위의_대기_승인_후보를_커서_끝까지_받는다() = runTest {
        server.enqueue(jsonResponse("""{"items":[${schedule(id = 1, seq = 30)}],"nextCursor":"c2"}"""))
        server.enqueue(jsonResponse("""{"items":[${todo(id = 2, seq = 12)}]}"""))

        repository.loadForChat(GROUP, fromSeq = 10, toSeq = null)

        val first = server.takeRequest().requestUrl!!
        assertEquals("/api/v1/groups/1/candidates", first.encodedPath)
        assertEquals("PENDING,APPROVED", first.queryParameter("status"))
        assertEquals("10", first.queryParameter("fromSeq"))
        assertNull(first.queryParameter("toSeq"))
        assertEquals("c2", server.takeRequest().requestUrl!!.queryParameter("cursor"))
        assertEquals(setOf(1L, 2L), repository.candidates(GROUP).first().map { it.id }.toSet())
    }

    @Test
    fun 일정_후보_값을_읽는다_가족_전체와_추정_항목() = runTest {
        server.enqueue(jsonResponse("""{"items":[${schedule(id = 1, seq = 30)}]}"""))

        repository.loadForChat(GROUP, 1, null)

        val candidate = repository.candidates(GROUP).first().single()
        val fields = candidate.fields as CandidateFields.Schedule
        assertEquals("할머니 댁 방문", fields.title)
        assertEquals(LocalDate.of(2026, 11, 14), fields.date)
        assertEquals(LocalTime.of(11, 0), fields.startTime)
        assertNull(fields.endTime)
        assertEquals("할머니 댁", fields.location?.text)
        assertEquals(7L, fields.location?.aliasId)
        assertTrue(fields.allMembers)
        assertTrue(fields.attendeeIds.isEmpty())
        assertTrue(candidate.isInferred("startTime.ampm"))
        assertEquals(CandidateSurface.CARD, candidate.surface)
        assertEquals(10231L, candidate.anchorMessageId)
    }

    @Test
    fun 할일_장소_기념일_후보_값을_읽는다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[${todo(id = 2, seq = 12)}, ${place(id = 3, seq = 13)},
                {"id":4,"groupId":1,"type":"ANNIVERSARY","status":"PENDING","version":0,"confidence":0.8,"surface":"INBOX",
                 "anchorMessageId":14,"anchorSeq":14,"createdAt":"2026-11-12T14:10:00+09:00",
                 "fields":{"title":"할머니 생신","month":12,"day":15,"personText":"할머니"}},
                {"id":5,"groupId":1,"type":"POLL","status":"PENDING","version":0,"createdAt":"2026-11-12T14:10:00+09:00"}]}""",
            ),
        )

        repository.loadForChat(GROUP, 1, null)

        val byId = repository.candidates(GROUP).first().associateBy { it.id }
        val todo = byId.getValue(2).fields as CandidateFields.Todo
        assertEquals(listOf(listOf(2L, 3L)), todo.assigneeChoices)
        assertEquals(LocalDate.of(2026, 11, 15), todo.dueDate)
        val place = byId.getValue(3).fields as CandidateFields.Place
        assertEquals(2, place.options.size)
        assertNull(place.chosen)
        assertEquals(PlaceCategory.RESTAURANT, place.options.first().category)
        assertEquals(3L, place.recommendedBy)
        val anniversary = byId.getValue(4).fields as CandidateFields.Anniversary
        assertEquals(12, anniversary.month)
        assertEquals(CandidateSurface.INBOX, byId.getValue(4).surface)
        // 모르는 유형은 그리지 않는다
        assertFalse(5L in byId)
    }

    @Test
    fun 다시_받으면_그_범위에서_사라진_후보는_뺀다() = runTest {
        server.enqueue(jsonResponse("""{"items":[${schedule(id = 1, seq = 30)}, ${todo(id = 2, seq = 12)}]}"""))
        server.enqueue(jsonResponse("""{"items":[${todo(id = 2, seq = 12)}]}"""))

        repository.loadForChat(GROUP, 10, null)
        repository.loadForChat(GROUP, 10, null)

        assertEquals(listOf(2L), repository.candidates(GROUP).first().map { it.id })
    }

    @Test
    fun 등록은_version만_보낸다() = runTest {
        val candidate = loadOne(schedule(id = 1, seq = 30))
        server.enqueue(jsonResponse("""{"candidate":${schedule(id = 1, seq = 30, status = "APPROVED", extra = APPROVED_EXTRA)}}"""))

        val result = repository.approve(candidate, approval = null)

        val body = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("version"), body.keys)
        assertEquals("3", body["version"]!!.jsonPrimitive.content)
        val approved = (result as ApiResult.Success).value
        assertEquals(CandidateStatus.APPROVED, approved.status)
        assertEquals(CandidateEntity.SCHEDULE, approved.entityType)
        assertEquals(CandidateStatus.APPROVED, repository.candidates(GROUP).first().single().status)
    }

    @Test
    fun 수정한_값은_유형에_맞는_요청_하나만_보낸다() = runTest {
        val candidate = loadOne(schedule(id = 1, seq = 30))
        server.enqueue(jsonResponse("""{"candidate":${schedule(id = 1, seq = 30, status = "APPROVED")}}"""))
        server.enqueue(jsonResponse("""{"candidate":${schedule(id = 1, seq = 30, status = "APPROVED")}}"""))
        server.enqueue(jsonResponse("""{"candidate":${schedule(id = 1, seq = 30, status = "APPROVED")}}"""))
        val noon = Instant.parse("2026-11-14T03:00:00Z")

        repository.approve(
            candidate,
            CandidateApproval.Schedule(
                ScheduleDraft("할머니 댁 방문", false, noon, noon, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14),
                    listOf(1, 2), null, null, Reminder.defaults(false)),
            ),
        )
        repository.approve(candidate, CandidateApproval.Todo(" 자동차 점검 ", 2, LocalDate.of(2026, 11, 15)))
        repository.approve(candidate, CandidateApproval.Place("k-2", PlaceCategory.RESTAURANT, "  "))

        val schedule = body().also { assertEquals(setOf("version", "schedule"), it.keys) }["schedule"]!!.jsonObject
        assertEquals("2026-11-14T12:00:00+09:00", schedule["startsAt"]!!.jsonPrimitive.content)
        val todo = body().also { assertEquals(setOf("version", "todo"), it.keys) }["todo"]!!.jsonObject
        assertEquals("자동차 점검", todo["title"]!!.jsonPrimitive.content)
        assertEquals("2026-11-15", todo["dueDate"]!!.jsonPrimitive.content)
        val place = body()["place"]!!.jsonObject
        assertEquals("k-2", place["kakaoPlaceId"]!!.jsonPrimitive.content)
        assertEquals("RESTAURANT", place["category"]!!.jsonPrimitive.content)
        // 빈 한줄평은 보내지 않는다
        assertFalse(place.containsKey("oneLineReview"))
    }

    @Test
    fun 채울_항목이_있으면_422와_항목_이름을_돌려준다() = runTest {
        val candidate = loadOne(todo(id = 2, seq = 12))
        server.enqueue(
            jsonResponse(
                """{"code":"CANDIDATE_MISSING_FIELDS","message":"담당자를 골라 주세요.","traceId":"t",
                   "fieldErrors":[{"field":"assigneeIds","reason":"choose"}]}""",
                422,
            ),
        )

        val failure = repository.approve(candidate, null) as ApiResult.Failure

        assertEquals(ErrorCodes.CANDIDATE_MISSING_FIELDS, failure.error?.code)
        assertEquals(listOf("assigneeIds"), failure.error?.fields)
        assertEquals(CandidateStatus.PENDING, repository.candidates(GROUP).first().single().status)
    }

    @Test
    fun 무시는_사유를_보내고_사유가_없으면_빼고_되돌리기는_본문_없이() = runTest {
        val candidate = loadOne(todo(id = 2, seq = 12))
        server.enqueue(jsonResponse("""{"candidate":${todo(id = 2, seq = 12, status = "DISMISSED")}}"""))
        server.enqueue(jsonResponse("""{"candidate":${todo(id = 2, seq = 12, status = "DISMISSED")}}"""))
        server.enqueue(jsonResponse("""{"candidate":${todo(id = 2, seq = 12, status = "UNDONE")}}"""))

        repository.dismiss(candidate, DismissReason.JOKE)
        repository.dismiss(candidate, null)
        repository.undo(candidate)

        server.takeRequest().let {
            assertEquals("/api/v1/candidates/2/dismiss", it.path)
            assertEquals("JOKE", testJson.parseToJsonElement(it.body.readUtf8()).jsonObject["reason"]!!.jsonPrimitive.content)
        }
        assertFalse(body().containsKey("reason"))
        assertEquals("/api/v1/candidates/2/undo", server.takeRequest().path)
        assertEquals(CandidateStatus.UNDONE, repository.candidates(GROUP).first().single().status)
    }

    @Test
    fun 상세는_근거_메시지와_강조_부분을_돌려준다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"candidate":${schedule(id = 1, seq = 30)},"evidence":[
                  {"messageId":10230,"seq":29,"senderId":2,"sentAt":"2026-11-12T14:09:00+09:00","spans":["토요일"],"text":"이번 토요일 어때"},
                  {"messageId":10231,"seq":30,"senderId":2,"sentAt":"2026-11-12T14:10:00+09:00","spans":["11시"],"text":null}]}""",
            ),
        )

        val detail = (repository.detail(1) as ApiResult.Success).value

        assertEquals(listOf(29L, 30L), detail.evidence.map { it.seq })
        assertEquals(listOf("토요일"), detail.evidence.first().spans)
        assertNull(detail.evidence.last().text)
    }

    @Test
    fun 실시간_새_후보는_더하고_처리_결과는_상태를_바꾼다() = runTest {
        repository.onGroupEvent(GROUP, event(ChatEventTypes.CANDIDATE_CREATED, schedule(id = 1, seq = 30)))
        assertEquals(CandidateStatus.PENDING, repository.candidates(GROUP).first().single().status)

        repository.onGroupEvent(
            GROUP,
            event(
                ChatEventTypes.CANDIDATE_RESOLVED,
                """{"groupId":1,"candidateId":1,"status":"APPROVED","decidedBy":2,"decidedByName":"엄마",
                   "entityType":"SCHEDULE","entityId":77,"linkedExisting":false}""",
            ),
        )

        val resolved = repository.candidates(GROUP).first().single()
        assertEquals(CandidateStatus.APPROVED, resolved.status)
        assertEquals(2L, resolved.decidedBy)
        assertEquals(77L, resolved.entityId)
        assertEquals(clock.instant(), resolved.decidedAt)
        // 채팅 이벤트는 건드리지 않는다
        repository.onGroupEvent(GROUP, event(ChatEventTypes.MESSAGE_CREATED, """{"id":1}"""))
        assertEquals(1, repository.candidates(GROUP).first().size)
    }

    @Test
    fun 제안함은_대기_후보를_한_쪽씩_받고_더_있는지_알려준다() = runTest {
        server.enqueue(jsonResponse("""{"items":[${todo(id = 2, seq = 12)}],"nextCursor":"c2"}"""))
        server.enqueue(jsonResponse("""{"items":[${schedule(id = 1, seq = 30)}]}"""))

        repository.loadInbox(GROUP)
        assertTrue(repository.inboxHasMore(GROUP).first())
        repository.loadInbox(GROUP, more = true)

        server.takeRequest().requestUrl!!.let {
            assertEquals("PENDING", it.queryParameter("status"))
            assertNull(it.queryParameter("cursor"))
            assertNull(it.queryParameter("fromSeq"))
        }
        assertEquals("c2", server.takeRequest().requestUrl!!.queryParameter("cursor"))
        assertFalse(repository.inboxHasMore(GROUP).first())
        assertEquals(setOf(1L, 2L), repository.candidates(GROUP).first().map { it.id }.toSet())
        // 끝까지 받았으면 더 요청하지 않는다
        repository.loadInbox(GROUP, more = true)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun 제안함_첫_쪽을_다시_받으면_그새_처리된_대기_후보는_빼고_승인한_칩은_남긴다() = runTest {
        server.enqueue(jsonResponse("""{"items":[${schedule(id = 1, seq = 30)}, ${todo(id = 2, seq = 12)}]}"""))
        server.enqueue(jsonResponse("""{"candidate":${schedule(id = 3, seq = 40, status = "APPROVED", extra = APPROVED_EXTRA)}}"""))
        server.enqueue(jsonResponse("""{"items":[${todo(id = 2, seq = 12)}]}"""))

        repository.loadInbox(GROUP)
        repository.detail(3)
        repository.loadInbox(GROUP)

        assertEquals(setOf(2L, 3L), repository.candidates(GROUP).first().map { it.id }.toSet())
    }

    private suspend fun loadOne(item: String) = run {
        server.enqueue(jsonResponse("""{"items":[$item]}"""))
        repository.loadForChat(GROUP, 1, null)
        server.takeRequest()
        repository.candidates(GROUP).first().single()
    }

    private fun body(): JsonObject = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun event(type: String, data: String) = ChatEventEnvelope(type, testJson.parseToJsonElement(data))

    companion object {
        const val GROUP = 1L
        const val APPROVED_EXTRA = ""","decidedBy":1,"decidedAt":"2026-11-12T14:20:00+09:00","entityType":"SCHEDULE","entityId":77"""

        fun schedule(id: Long, seq: Long, status: String = "PENDING", extra: String = "") = """
            {"id":$id,"groupId":1,"type":"SCHEDULE","status":"$status","version":3,"confidence":0.9,"surface":"CARD",
             "anchorMessageId":10231,"anchorSeq":$seq,"createdAt":"2026-11-12T14:10:30+09:00",
             "expiresAt":"2026-11-14T11:00:00+09:00","inferred":["startTime.ampm","attendeeIds"],"missingFields":[],
             "fields":{"title":"할머니 댁 방문","date":"2026-11-14","startTime":"11:00","endTime":null,"allDay":false,
                       "location":{"text":"할머니 댁","aliasId":7,"placeId":null},"attendeeIds":["ALL"],
                       "attendeeChoices":[],"unmatchedAttendees":[]}$extra}
        """.trimIndent()

        fun todo(id: Long, seq: Long, status: String = "PENDING") = """
            {"id":$id,"groupId":1,"type":"TODO","status":"$status","version":1,"confidence":0.8,"surface":"CARD",
             "anchorMessageId":$seq,"anchorSeq":$seq,"createdAt":"2026-11-12T14:12:30+09:00","inferred":["dueDate"],
             "fields":{"title":"자동차 점검 맡기기","dueDate":"2026-11-15","assigneeIds":[],"assigneeChoices":[[2,3]],
                       "unmatchedAssignees":[]}}
        """.trimIndent()

        fun place(id: Long, seq: Long) = """
            {"id":$id,"groupId":1,"type":"PLACE","status":"PENDING","version":0,"confidence":0.85,"surface":"CARD",
             "anchorMessageId":$seq,"anchorSeq":$seq,"createdAt":"2026-11-12T15:05:30+09:00",
             "fields":{"name":"OO칼국수","regionHint":"잠실","kakaoPlaceId":null,"opinion":"칼국수가 정말 맛있음","recommendedBy":3,
                       "options":[{"kakaoPlaceId":"k-1","name":"OO칼국수 잠실점","category":"RESTAURANT","address":"서울 송파구",
                                   "region1":"서울","region2":"송파구","lat":37.5,"lng":127.1},
                                  {"kakaoPlaceId":"k-2","name":"OO칼국수 본점","category":"RESTAURANT","region1":"서울","region2":"송파구"}]}}
        """.trimIndent()
    }
}
