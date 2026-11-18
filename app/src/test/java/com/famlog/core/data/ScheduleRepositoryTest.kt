package com.famlog.core.data

import app.cash.turbine.test
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.Reminder
import com.famlog.core.model.Schedule
import com.famlog.core.model.ScheduleDraft
import com.famlog.core.model.errorCode
import com.famlog.core.network.ScheduleApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ScheduleRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        server.start()
        repository = ScheduleRepository(server.api<ScheduleApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 기간_조회는_서울_날짜로_묻고_회차를_서울_날짜에_걸친다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[
                  {"scheduleId":1,"title":"저녁","allDay":false,"startsAt":"2026-11-21T22:00:00+09:00","endsAt":"2026-11-22T00:00:00+09:00",
                   "participantIds":[3],"createdBy":3,"recurring":false},
                  {"scheduleId":2,"title":"여행","allDay":true,"startsAt":"2026-11-21T00:00:00+09:00","endsAt":"2026-11-24T00:00:00+09:00",
                   "startDate":"2026-11-21","endDate":"2026-11-23","participantIds":[3,4],"recurring":true}
                ]}""",
            ),
        )

        val result = repository.occurrences(GROUP, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 5))

        assertEquals("/api/v1/groups/$GROUP/schedules?from=2026-11-01&to=2026-12-05", server.takeRequest().path)
        val items = (result as ApiResult.Success).value
        // 22:00~다음 날 00:00은 하루짜리
        assertEquals(LocalDate.of(2026, 11, 21), items[0].startDate)
        assertEquals(LocalDate.of(2026, 11, 21), items[0].endDate)
        assertEquals(LocalDate.of(2026, 11, 23), items[1].endDate)
        assertTrue(items[1].recurring)
    }

    @Test
    fun 겹침_조회는_시각의_플러스를_인코딩하고_구성원을_반복_파라미터로_보낸다() = runTest {
        server.enqueue(jsonResponse("""{"items":[{"scheduleId":9,"title":"치과","allDay":false,"userIds":[4]}]}"""))

        val result = repository.conflicts(
            GROUP,
            Instant.parse("2026-11-21T09:00:00Z"),
            Instant.parse("2026-11-21T10:00:00Z"),
            memberIds = listOf(3, 4),
            excludeId = 7,
        )

        val url = server.takeRequest().requestUrl!!
        val path = url.encodedQuery!!
        // "+"가 그대로 가면 서버가 공백으로 읽는다
        assertTrue(path, path.contains("startsAt=2026-11-21T18%3A00%3A00%2B09%3A00"))
        assertEquals("2026-11-21T19:00:00+09:00", url.queryParameter("endsAt"))
        assertTrue(path, path.contains("memberIds=3&memberIds=4"))
        assertTrue(path, path.contains("excludeId=7"))
        assertEquals(listOf(4L), (result as ApiResult.Success).value.single().userIds)
    }

    @Test
    fun 시간_일정은_시각만_종일_일정은_날짜만_보내고_알림은_빈_목록도_보낸다() = runTest {
        server.enqueue(jsonResponse(scheduleJson(id = 1), code = 201))
        server.enqueue(jsonResponse(scheduleJson(id = 2, allDay = true), code = 201))

        repository.create(GROUP, timedDraft())
        repository.create(GROUP, timedDraft().copy(allDay = true, startDate = LocalDate.of(2026, 11, 21), endDate = LocalDate.of(2026, 11, 22), reminders = emptyList()))

        val timed = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("2026-11-21T18:00:00+09:00", timed["startsAt"]!!.jsonPrimitive.content)
        assertNull(timed["startDate"])
        assertEquals("FREQ=WEEKLY", timed["rrule"]!!.jsonPrimitive.content)
        assertEquals(listOf("PREV_DAY_20", "HOUR_1"), (timed["reminders"] as JsonArray).map { it.jsonPrimitive.content })
        assertNull(timed["version"])

        val allDay = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertNull(allDay["startsAt"])
        assertEquals("2026-11-22", allDay["endDate"]!!.jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), allDay["reminders"])
    }

    @Test
    fun 수정은_조회한_version을_보내고_충돌이면_VERSION_CONFLICT() = runTest {
        server.enqueue(errorResponse(409, ErrorCodes.VERSION_CONFLICT))
        val schedule = schedule(version = 4)

        val result = repository.update(schedule, timedDraft())

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/schedules/7", request.path)
        assertEquals(4L, testJson.parseToJsonElement(request.body.readUtf8()).jsonObject["version"]!!.jsonPrimitive.content.toLong())
        assertEquals(ErrorCodes.VERSION_CONFLICT, result.errorCode)
    }

    @Test
    fun 등록_수정_삭제가_성공하면_그_그룹이_바뀌었다고_알린다() = runTest {
        server.enqueue(jsonResponse(scheduleJson(id = 1), code = 201))
        server.enqueue(errorResponse(403, ErrorCodes.GROUP_ROLE_REQUIRED))
        server.enqueue(MockResponse().setResponseCode(204))

        repository.changes.test {
            repository.create(GROUP, timedDraft())
            assertEquals(GROUP, awaitItem())
            repository.update(schedule(), timedDraft())
            repository.delete(schedule())
            assertEquals(GROUP, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun 공휴일은_해마다_한_번만_받는다() = runTest {
        server.enqueue(jsonResponse("""{"items":[{"date":"2026-12-25","name":"성탄절","substitute":false}]}"""))

        repository.holidays(2026)
        val second = repository.holidays(2026)

        assertEquals(1, server.requestCount)
        assertEquals("/api/v1/holidays?year=2026", server.takeRequest().path)
        assertEquals("성탄절", (second as ApiResult.Success).value.single().name)
    }

    @Test
    fun 공휴일을_받지_못하면_다음에_다시_묻는다() = runTest {
        server.enqueue(errorResponse(500, "INTERNAL_ERROR"))
        server.enqueue(jsonResponse("""{"items":[]}"""))

        assertFalse(repository.holidays(2028) is ApiResult.Success)
        assertTrue(repository.holidays(2028) is ApiResult.Success)
        assertEquals(2, server.requestCount)
    }

    private fun timedDraft() = ScheduleDraft(
        title = "할머니 생신 저녁",
        allDay = false,
        startsAt = Instant.parse("2026-11-21T09:00:00Z"),
        endsAt = Instant.parse("2026-11-21T11:00:00Z"),
        startDate = null,
        endDate = null,
        participantIds = listOf(3, 4),
        rrule = "FREQ=WEEKLY",
        memo = null,
        reminders = listOf(Reminder.PREV_DAY_20, Reminder.HOUR_1),
    )

    private fun schedule(version: Long = 1) = Schedule(
        id = 7, groupId = GROUP, title = "저녁", allDay = false,
        startsAt = Instant.parse("2026-11-21T09:00:00Z"), endsAt = Instant.parse("2026-11-21T10:00:00Z"),
        startDate = LocalDate.of(2026, 11, 21), endDate = LocalDate.of(2026, 11, 21),
        participantIds = listOf(3), rrule = null, memo = null, reminders = emptyList(), createdBy = 3, version = version,
    )

    private fun scheduleJson(id: Long, allDay: Boolean = false) = """{"id":$id,"groupId":$GROUP,"title":"저녁","allDay":$allDay,
        "startsAt":"2026-11-21T18:00:00+09:00","endsAt":"2026-11-21T20:00:00+09:00","participantIds":[3],
        "reminders":["PREV_DAY_20"],"createdBy":3,"version":1}"""

    private companion object {
        const val GROUP = 5L

    }
}
