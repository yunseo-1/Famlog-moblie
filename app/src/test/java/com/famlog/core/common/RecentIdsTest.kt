package com.famlog.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentIdsTest {

    @Test
    fun 처음_보는_ID만_true다() {
        val ids = RecentIds(capacity = 3)

        assertTrue(ids.add(1))
        assertFalse(ids.add(1))
        assertTrue(ids.add(2))
    }

    @Test
    fun 가득_차면_가장_오래된_ID를_잊는다() {
        val ids = RecentIds(capacity = 2)
        ids.add(1)
        ids.add(2)
        ids.add(3)

        assertTrue(ids.add(1))
        assertFalse(ids.add(3))
    }
}
