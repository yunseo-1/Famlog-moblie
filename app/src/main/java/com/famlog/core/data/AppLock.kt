package com.famlog.core.data

import com.famlog.core.common.ApplicationScope
import com.famlog.core.datastore.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 잠금 (SEC-01). 켜져 있으면 앱을 새로 열 때와 [RELOCK_AFTER] 넘게 나갔다 돌아올 때 잠근다.
 * 사진 고르기·생체 인증 창처럼 잠깐 다른 화면에 다녀오는 것은 다시 묻지 않는다. 푸는 것은 기기 인증(생체·화면 잠금)
 */
@Singleton
class AppLock @Inject constructor(
    private val prefs: AppPrefs,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _locked = MutableStateFlow<Boolean?>(null)

    /** null이면 아직 설정을 읽는 중 — 화면을 보이지 않는다 */
    val locked: StateFlow<Boolean?> = _locked.asStateFlow()

    private var backgroundAt: Instant? = null

    /** 앱 시작 때. 켜져 있으면 잠근 채로 시작한다 */
    fun start() {
        scope.launch {
            val enabled = prefs.appLockEnabled()
            _enabled.value = enabled
            _locked.compareAndSet(null, enabled)
        }
    }

    fun onBackground() {
        backgroundAt = clock.instant()
    }

    fun onForeground() {
        val since = backgroundAt ?: return
        backgroundAt = null
        if (_enabled.value && Duration.between(since, clock.instant()) >= RELOCK_AFTER) _locked.value = true
    }

    /** 기기 인증에 성공했다 */
    fun unlock() {
        _locked.value = false
    }

    /** 켜고 끄기 전에 화면이 기기 인증을 받는다 */
    suspend fun setEnabled(enabled: Boolean) {
        prefs.setAppLockEnabled(enabled)
        _enabled.value = enabled
        if (!enabled) _locked.value = false
    }

    companion object {
        val RELOCK_AFTER: Duration = Duration.ofMinutes(1)
    }
}
