package com.famlog.core.network

import com.famlog.core.model.ItemSource
import com.famlog.testing.testJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 일정·회차·할 일·장소 응답의 source (ARC-06). candidateId는 백엔드가 붙이면 오고, 없어도 읽는다 */
class ItemSourceParsingTest {

    @Test
    fun 일정과_회차는_원문_위치를_읽고_직접_등록한_일정은_null() {
        val schedule = testJson.decodeFromString<ScheduleResponse>(
            """{"id":6,"groupId":1,"title":"할머니 댁 방문","allDay":false,"startsAt":"2026-11-21T11:00:00+09:00",
               "endsAt":"2026-11-21T11:00:00+09:00","version":0,"source":{"messageId":35,"seq":35}}""",
        ).toModel()
        val occurrence = testJson.decodeFromString<OccurrenceResponse>(
            """{"scheduleId":6,"title":"할머니 댁 방문","allDay":false,"startsAt":"2026-11-21T11:00:00+09:00",
               "endsAt":"2026-11-21T11:00:00+09:00","source":{"messageId":35,"seq":35,"candidateId":8}}""",
        ).toModel()
        val direct = testJson.decodeFromString<ScheduleResponse>(
            """{"id":7,"groupId":1,"title":"치과","allDay":true,"startsAt":"2026-11-21T00:00:00+09:00",
               "endsAt":"2026-11-22T00:00:00+09:00","version":0,"source":null}""",
        ).toModel()

        assertEquals(ItemSource(35, 35), schedule.source)
        assertEquals(ItemSource(35, 35, candidateId = 8), occurrence.source)
        assertNull(direct.source)
    }

    @Test
    fun 할_일과_장소도_원문_위치를_읽는다() {
        val todo = testJson.decodeFromString<TodoResponse>(
            """{"id":2,"groupId":1,"title":"자동차 점검 맡기기","version":0,"source":{"messageId":28,"seq":28,"candidateId":5}}""",
        ).toModel()
        val place = testJson.decodeFromString<PlaceResponse>(
            """{"id":1,"groupId":1,"kakaoPlaceId":"k1","name":"해주냉면","category":"RESTAURANT","lat":37.5,"lng":127.1,
               "createdAt":"2026-11-19T10:12:00+09:00","source":{"messageId":29,"seq":29}}""",
        ).toModel()

        assertEquals(ItemSource(28, 28, candidateId = 5), todo.source)
        assertEquals(ItemSource(29, 29), place.source)
    }
}
