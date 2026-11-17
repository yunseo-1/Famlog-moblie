package com.famlog.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** 서버 AgePolicy와 같은 경계: 14번째 생일이 있는 달까지는 불가, 다음 달부터 가능 */
class AgePolicyTest {

    private val now = YearMonth.of(2026, 11)

    @Test
    fun 만_14세_경계() {
        assertFalse(AgePolicy.isOldEnough(YearMonth.of(2012, 12), now)) // 아직 13세
        assertFalse(AgePolicy.isOldEnough(YearMonth.of(2012, 11), now)) // 생일이 있는 달: 날짜를 몰라 불가
        assertTrue(AgePolicy.isOldEnough(YearMonth.of(2012, 10), now))
        assertTrue(AgePolicy.isOldEnough(YearMonth.of(1990, 3), now))
    }

    @Test
    fun 형식이_틀리거나_미래이거나_1900년_이전이면_null() {
        assertEquals(YearMonth.of(1990, 3), AgePolicy.parse("199003", now))
        assertNull(AgePolicy.parse("199013", now))
        assertNull(AgePolicy.parse("19903", now))
        assertNull(AgePolicy.parse("1990-3", now))
        assertNull(AgePolicy.parse("202612", now))
        assertNull(AgePolicy.parse("189912", now))
        assertEquals(now, AgePolicy.parse("202611", now))
    }
}
