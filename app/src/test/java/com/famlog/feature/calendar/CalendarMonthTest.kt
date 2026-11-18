package com.famlog.feature.calendar

import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthTest {

    @Test
    fun 월_격자는_일요일부터_토요일까지_그_달이_걸친_주만큼() {
        val november = monthGrid(YearMonth.of(2026, 11))
        // 2026-11-01은 일요일, 11-30은 월요일 → 5주
        assertEquals(LocalDate.of(2026, 11, 1), november.first())
        assertEquals(LocalDate.of(2026, 12, 5), november.last())
        assertEquals(35, november.size)

        val august = monthGrid(YearMonth.of(2026, 8))
        assertEquals(DayOfWeek.SUNDAY, august.first().dayOfWeek)
        assertEquals(42, august.size)
    }

    /** 인수 기준 SCH-02: 엄마 일정 3개·아빠 일정 2개일 때 "엄마" 필터는 3개, "전체"는 5개 */
    @Test
    fun 엄마를_고르면_엄마가_참여한_일정만_전체는_모두() {
        val items = listOf(
            occurrence(1, MOM), occurrence(2, MOM), occurrence(3, MOM),
            occurrence(4, DAD), occurrence(5, DAD),
        )

        assertEquals(3, items.filterByMembers(setOf(MOM)).size)
        assertEquals(5, items.filterByMembers(emptySet()).size)
        assertEquals(5, items.filterByMembers(setOf(MOM, DAD)).size)
    }

    @Test
    fun 여러_명이_참여한_일정은_그중_한_명만_골라도_보인다() {
        val together = occurrence(1, MOM, DAD)

        assertEquals(listOf(together), listOf(together).filterByMembers(setOf(DAD)))
    }

    @Test
    fun 그날_일정은_종일_먼저_그다음_시작_순이고_여러_날_일정은_걸친_날마다_나온다() {
        val trip = occurrence(1, MOM, start = "2026-11-20", end = "2026-11-22", allDay = true)
        val dinner = occurrence(2, DAD, start = "2026-11-21", hour = 18)
        val lunch = occurrence(3, DAD, start = "2026-11-21", hour = 12)
        val items = listOf(dinner, trip, lunch)

        assertEquals(listOf(trip, lunch, dinner), items.on(LocalDate.of(2026, 11, 21)))
        assertEquals(listOf(trip), items.on(LocalDate.of(2026, 11, 22)))
        assertEquals(
            listOf(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 22)),
            items.sectionsIn(YearMonth.of(2026, 11)).map { it.first },
        )
    }

    @Test
    fun 점은_그날_참여자를_구성원_순서로_고른_사람만() {
        val items = listOf(occurrence(1, DAD, MOM), occurrence(2, KID))
        val date = LocalDate.of(2026, 11, 21)

        assertEquals(listOf(DAD, MOM, KID), items.dotMembers(date, members, emptySet()).map { it.userId })
        assertEquals(listOf(MOM), items.dotMembers(date, members, setOf(MOM)).map { it.userId })
    }

    @Test
    fun 받침에_따라_은_는을_고른다() {
        assertEquals("는", topicParticle("아빠"))
        assertEquals("은", topicParticle("동생"))
        assertEquals("는", topicParticle("아빠·엄마"))
        assertEquals("은(는)", topicParticle("Mia"))
    }

    private val members = listOf(member(DAD, "아빠"), member(MOM, "엄마"), member(KID, "동생"))

    private fun member(id: Long, title: String) = Member(id, title, GroupRole.MEMBER, null, title, null)

    private fun occurrence(
        id: Long,
        vararg participants: Long,
        start: String = "2026-11-21",
        end: String = start,
        hour: Int = 9,
        allDay: Boolean = false,
    ): Occurrence {
        val startDate = LocalDate.parse(start)
        val endDate = LocalDate.parse(end)
        val startsAt = Instant.parse("${start}T00:00:00Z").plusSeconds(hour * 3600L)
        return Occurrence(
            scheduleId = id, title = "일정 $id", allDay = allDay,
            startsAt = startsAt, endsAt = startsAt.plusSeconds(3600),
            startDate = startDate, endDate = endDate,
            participantIds = participants.toList(), createdBy = participants.first(), recurring = false,
        )
    }

    private companion object {
        const val DAD = 1L
        const val MOM = 2L
        const val KID = 3L
    }
}
