package com.famlog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 버전 비교: 서버 규칙과 같아야 한다 (자리별 숫자, 빠진 자리 0, 꼬리 무시, 숫자가 아니면 비교 안 함) */
class AppVersionsTest {

    @Test
    fun 자리별_숫자로_비교하고_빠진_자리는_0() {
        assertEquals(0, AppVersions.compare("1.0", "1.0.0"))
        assertTrue(AppVersions.compare("1.0", "1.0.1")!! < 0)
        assertTrue(AppVersions.compare("1.10", "1.9")!! > 0)
        assertTrue(AppVersions.compare("2", "1.9.9")!! > 0)
        assertEquals(0, AppVersions.compare("1.2.3.4", "1.2.3.4"))
    }

    @Test
    fun 꼬리는_무시하고_숫자가_아니면_비교하지_않는다() {
        assertEquals(0, AppVersions.compare("1.0.0-beta", "1.0"))
        assertEquals(0, AppVersions.compare("1.0+3", "1.0.0"))
        assertNull(AppVersions.compare("1.x", "1.0"))
        assertNull(AppVersions.compare("1.2.3.4.5", "1.0"))
        assertNull(AppVersions.compare("", "1.0"))
    }

    @Test
    fun 낮은지는_기준이_없거나_비교할_수_없으면_아니다() {
        assertTrue(AppVersions.isLower("1.0", "1.0.1"))
        assertFalse(AppVersions.isLower("1.0", "1.0.0"))
        assertFalse(AppVersions.isLower("1.0", null))
        assertFalse(AppVersions.isLower("1.0", "abc"))
        assertFalse(AppVersions.isLower("1.0", "0.0.0"))
    }
}
