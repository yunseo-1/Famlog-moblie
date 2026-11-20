package com.famlog.feature.chat

import com.famlog.core.model.ScheduleTiming
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 변경 후보 카드의 "변경 전 → 후": 바뀐 부분만 */
class TimingChangeTest {

    private val sat = LocalDate.of(2026, 11, 21)
    private val sun = LocalDate.of(2026, 11, 22)

    private fun at(date: LocalDate, hour: Int?) =
        ScheduleTiming(date, hour?.let { LocalTime.of(it, 0) }, hour?.let { LocalTime.of(it + 1, 0) }, allDay = hour == null)

    @Test
    fun 시각만_바뀌면_날짜는_앞에_두고_시각만() {
        assertEquals(TimingChange("11/21(토) ", "오전 11:00", "오후 12:00", ""), timingChange(at(sat, 11), at(sat, 12), "종일"))
    }

    @Test
    fun 날짜만_바뀌면_날짜만_바꾸고_시각은_뒤에() {
        assertEquals(TimingChange("", "11/21(토)", "11/22(일)", " 오전 11:00"), timingChange(at(sat, 11), at(sun, 11), "종일"))
        assertEquals(TimingChange("", "11/21(토)", "11/22(일)", " 종일"), timingChange(at(sat, null), at(sun, null), "종일"))
    }

    @Test
    fun 둘_다_바뀌거나_종일에서_시각을_정하면_그_부분까지() {
        assertEquals(TimingChange("", "11/21(토) 오전 11:00", "11/22(일) 오후 3:00", ""), timingChange(at(sat, 11), at(sun, 15), "종일"))
        assertEquals(TimingChange("11/21(토) ", "종일", "오후 12:00", ""), timingChange(at(sat, null), at(sat, 12), "종일"))
    }
}
