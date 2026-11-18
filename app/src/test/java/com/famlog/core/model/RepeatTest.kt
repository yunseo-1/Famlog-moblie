package com.famlog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RepeatTest {

    @Test
    fun 종류와_종료일을_RRULE로_만든다() {
        assertEquals("FREQ=WEEKLY", Repeat(RepeatFreq.WEEKLY).toRrule())
        assertEquals("FREQ=MONTHLY;UNTIL=20261231", Repeat(RepeatFreq.MONTHLY, until = LocalDate.of(2026, 12, 31)).toRrule())
    }

    @Test
    fun 서버가_저장한_규칙을_읽고_간격과_횟수를_지킨다() {
        val repeat = Repeat.parse("FREQ=WEEKLY;INTERVAL=2;COUNT=10")!!

        assertEquals(Repeat(RepeatFreq.WEEKLY, interval = 2, count = 10), repeat)
        assertEquals("FREQ=WEEKLY;INTERVAL=2;COUNT=10", repeat.toRrule())
        // 종료일을 정하면 횟수 대신 종료일을 쓴다
        assertEquals(
            "FREQ=WEEKLY;INTERVAL=2;UNTIL=20270101",
            repeat.copy(until = LocalDate.of(2027, 1, 1)).toRrule(),
        )
    }

    @Test
    fun 시각이_붙은_UNTIL도_날짜로_읽는다() {
        assertEquals(LocalDate.of(2026, 12, 31), Repeat.parse("FREQ=YEARLY;UNTIL=20261231T145959Z")!!.until)
    }

    @Test
    fun 없거나_모르는_규칙은_null() {
        assertNull(Repeat.parse(null))
        assertNull(Repeat.parse(""))
        assertNull(Repeat.parse("FREQ=DAILY"))
    }
}
