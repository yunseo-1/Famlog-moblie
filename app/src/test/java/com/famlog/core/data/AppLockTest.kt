package com.famlog.core.data

import com.famlog.core.datastore.AppPrefs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 앱 잠금 (SEC-01): 언제 잠그는지 */
class AppLockTest {

    private class MemoryPrefs(var lock: Boolean) : AppPrefs {
        override suspend fun notificationPromptShown() = false
        override suspend fun setNotificationPromptShown() = Unit
        override suspend fun seenNoticeUpdatedAt(): String? = null
        override suspend fun setSeenNoticeUpdatedAt(value: String?) = Unit
        override suspend fun appLockEnabled() = lock
        override suspend fun setAppLockEnabled(enabled: Boolean) {
            lock = enabled
        }
    }

    private class MovingClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) {
            now += duration
        }
    }

    private val clock = MovingClock(Instant.parse("2026-11-23T01:00:00Z"))

    private fun TestScope.started(enabled: Boolean, prefs: MemoryPrefs = MemoryPrefs(enabled)) =
        AppLock(prefs, clock, backgroundScope).also {
            assertNull(it.locked.value)
            it.start()
            runCurrent()
        }

    @Test
    fun 켜져_있으면_앱을_열_때_잠기고_인증하면_풀린다() = runTest {
        val lock = started(enabled = true)

        assertEquals(true, lock.locked.value)
        lock.unlock()
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun 꺼져_있으면_잠그지_않는다() = runTest {
        val lock = started(enabled = false)

        lock.onBackground()
        clock.advance(Duration.ofMinutes(10))
        lock.onForeground()

        assertEquals(false, lock.locked.value)
    }

    @Test
    fun 일분_안에_돌아오면_그대로이고_넘으면_잠근다() = runTest {
        val lock = started(enabled = true)
        lock.unlock()

        lock.onBackground()
        clock.advance(Duration.ofSeconds(59))
        lock.onForeground()
        assertEquals(false, lock.locked.value)

        lock.onBackground()
        clock.advance(AppLock.RELOCK_AFTER)
        lock.onForeground()
        assertEquals(true, lock.locked.value)
    }

    @Test
    fun 켜고_끄면_저장하고_끄면_바로_풀린다() = runTest {
        val prefs = MemoryPrefs(false)
        val lock = started(enabled = false, prefs = prefs)

        lock.setEnabled(true)
        assertTrue(prefs.lock)
        assertTrue(lock.enabled.value)
        // 켤 때는 이미 인증한 상태라 바로 잠그지 않는다
        assertEquals(false, lock.locked.value)

        lock.onBackground()
        clock.advance(Duration.ofMinutes(2))
        lock.onForeground()
        assertEquals(true, lock.locked.value)

        lock.setEnabled(false)
        assertFalse(prefs.lock)
        assertEquals(false, lock.locked.value)
    }
}
