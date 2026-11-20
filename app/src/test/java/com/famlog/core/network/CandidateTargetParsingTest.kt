package com.famlog.core.network

import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateTarget
import com.famlog.core.model.GroupRole
import com.famlog.core.model.ScheduleTiming
import com.famlog.core.model.TargetAction
import com.famlog.core.model.TargetItem
import com.famlog.testing.testJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 기존 일정 변경·할 일 완료 후보의 target (백엔드 HANDOFF "기존 항목 변경·완료 후보") */
class CandidateTargetParsingTest {

    private fun parse(type: String, fields: String, target: String) = testJson.decodeFromString<CandidateResponse>(
        """{"id":9,"groupId":1,"type":"$type","status":"PENDING","fields":$fields,"confidence":0.9,"surface":"CARD",
           "anchorMessageId":40,"anchorSeq":40,"version":0,"createdAt":"2026-11-20T15:10:00+09:00","target":$target}""",
    ).toModel()

    @Test
    fun 일정_변경은_대상과_바꾸기_전후_시각을_읽는다() {
        val candidate = parse(
            "SCHEDULE",
            """{"title":"영화 보기","date":"2026-11-21","startTime":"12:00","endTime":null,"allDay":false}""",
            """{"entityType":"SCHEDULE","action":"CHANGE","entityId":6,"title":"영화 보기","createdBy":2,
               "before":{"date":"2026-11-21","startTime":"11:00","endTime":"13:00","allDay":false,"startsAt":"2026-11-21T11:00:00+09:00","endsAt":"2026-11-21T13:00:00+09:00"},
               "after":{"date":"2026-11-21","startTime":"12:00","endTime":"14:00","allDay":false,"startsAt":"2026-11-21T12:00:00+09:00","endsAt":"2026-11-21T14:00:00+09:00"},
               "choices":[]}""",
        )!!

        val target = candidate.target!!
        assertEquals(CandidateEntity.SCHEDULE, target.entityType)
        assertEquals(TargetAction.CHANGE, target.action)
        assertEquals(
            TargetItem(
                entityId = 6, title = "영화 보기", createdBy = 2,
                before = ScheduleTiming(LocalDate.of(2026, 11, 21), LocalTime.of(11, 0), LocalTime.of(13, 0), allDay = false),
                after = ScheduleTiming(LocalDate.of(2026, 11, 21), LocalTime.of(12, 0), LocalTime.of(14, 0), allDay = false),
            ),
            target.item,
        )
    }

    @Test
    fun 대상이_여럿이면_고를_목록만_있고_종일은_시각이_없다() {
        val candidate = parse(
            "SCHEDULE",
            """{"title":null,"date":"2026-11-22","startTime":null,"endTime":null,"allDay":true}""",
            """{"entityType":"SCHEDULE","action":"CHANGE","entityId":null,"title":null,"createdBy":null,"before":null,"after":null,
               "choices":[
                 {"entityId":6,"title":"영화 보기","createdBy":2,
                  "before":{"date":"2026-11-21","startTime":null,"endTime":null,"allDay":true},
                  "after":{"date":"2026-11-22","startTime":null,"endTime":null,"allDay":true}},
                 {"entityId":7,"title":"치과","createdBy":1,
                  "before":{"date":"2026-11-21","startTime":"09:00","endTime":"10:00","allDay":false},
                  "after":{"date":"2026-11-22","startTime":"09:00","endTime":"10:00","allDay":false}}]}""",
        )!!

        val target = candidate.target!!
        assertNull(target.item)
        assertEquals(listOf(6L, 7L), target.choices.map { it.entityId })
        assertEquals(ScheduleTiming(LocalDate.of(2026, 11, 21), null, null, allDay = true), target.choices[0].before)
        assertNull(target.resolve(null))
        assertEquals(7L, target.resolve(7)?.entityId)
        assertNull(target.resolve(99))
    }

    @Test
    fun 할_일_완료는_지금_담당과_기한을_읽는다() {
        val candidate = parse(
            "TODO",
            """{"title":"세제 사 오기"}""",
            """{"entityType":"TODO","action":"COMPLETE","entityId":3,"title":"세제 사 오기",
               "before":{"assigneeId":2,"dueDate":"2026-11-22","done":false},"choices":[]}""",
        )!!

        assertEquals(
            CandidateTarget(
                CandidateEntity.TODO,
                TargetAction.COMPLETE,
                TargetItem(entityId = 3, title = "세제 사 오기", dueDate = LocalDate.of(2026, 11, 22), assigneeId = 2),
                emptyList(),
            ),
            candidate.target,
        )
    }

    @Test
    fun 새_항목_후보는_대상이_없고_읽지_못한_대상이면_후보를_뺀다() {
        assertNull(parse("TODO", """{"title":"세제 사 오기"}""", "null")!!.target)
        // 새 항목 카드로 잘못 보이지 않게, 모르는 action·대상 없는 target은 그리지 않는다
        assertNull(parse("TODO", """{"title":"세제"}""", """{"entityType":"TODO","action":"DELETE","entityId":3,"choices":[]}"""))
        assertNull(parse("TODO", """{"title":"세제"}""", """{"entityType":"TODO","action":"COMPLETE","entityId":null,"choices":[]}"""))
    }

    @Test
    fun 일정_변경은_등록한_사람과_관리자만_할_일_완료는_누구나() {
        val mine = TargetItem(entityId = 6, title = "영화", createdBy = 1)
        val change = CandidateTarget(CandidateEntity.SCHEDULE, TargetAction.CHANGE, mine, emptyList())
        val complete = change.copy(entityType = CandidateEntity.TODO, action = TargetAction.COMPLETE)

        assertTrue(change.allows(mine, me = 1, role = GroupRole.MEMBER))
        assertFalse(change.allows(mine, me = 2, role = GroupRole.MEMBER))
        assertTrue(change.allows(mine, me = 2, role = GroupRole.ADMIN))
        assertTrue(change.allows(mine, me = 2, role = GroupRole.OWNER))
        assertTrue(complete.allows(mine, me = 2, role = GroupRole.MEMBER))
    }
}
