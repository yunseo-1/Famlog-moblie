package com.famlog

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.core.data.AppLock
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.security.DeviceAuth
import com.famlog.feature.settings.AppLockContent
import com.famlog.core.push.PushNotifier
import com.famlog.debug.BuildTypeSetup
import com.famlog.navigation.FamlogNavHost
import com.famlog.navigation.OpenTarget
import com.famlog.navigation.PendingOpen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var pendingOpen: PendingOpen

    @Inject lateinit var appLock: AppLock

    private var authenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BuildTypeSetup.onCreate(this)
        // 화면 회전 등으로 다시 만들어질 때는 같은 알림 탭을 다시 처리하지 않는다
        if (savedInstanceState == null) handleNotificationTap(intent)
        setContent {
            FamLogTheme {
                val locked by appLock.locked.collectAsStateWithLifecycle()
                val lockEnabled by appLock.enabled.collectAsStateWithLifecycle()
                LaunchedEffect(lockEnabled) {
                    // 앱 잠금을 켜면 최근 앱 화면의 미리보기만 가린다(스크린샷은 그대로). 12 이하는 가리지 않는다 (사용자 결정)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!lockEnabled)
                }
                Box(Modifier.fillMaxSize()) {
                    FamlogNavHost()
                    when (locked) {
                        // 설정을 읽는 동안은 아래 화면을 보이지 않는다
                        null -> Box(Modifier.fillMaxSize().background(FamTheme.colors.bgDefault))
                        true -> {
                            LaunchedEffect(Unit) { unlock() }
                            BackHandler { moveTaskToBack(true) }
                            AppLockContent(onUnlock = ::unlock)
                        }
                        false -> Unit
                    }
                }
            }
        }
    }

    /** 기기 인증(생체·화면 잠금)에 성공하면 잠금을 푼다. 취소하면 잠금 화면에 머문다 */
    private fun unlock() {
        if (authenticating) return
        authenticating = true
        DeviceAuth.authenticate(this, getString(R.string.app_lock_prompt_title), getString(R.string.app_lock_prompt_subtitle)) { ok ->
            authenticating = false
            if (ok) appLock.unlock()
        }
    }

    /** 앱이 떠 있을 때 알림을 누르면 (CLEAR_TOP·SINGLE_TOP) 여기로 온다 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationTap(intent)
    }

    private fun handleNotificationTap(intent: Intent) {
        // 최근 앱 목록에서 다시 열면 예전 알림의 intent가 그대로 오므로 무시한다
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val groupId = intent.getLongExtra(PushNotifier.EXTRA_GROUP_ID, -1L)
        val scheduleId = intent.getLongExtra(PushNotifier.EXTRA_SCHEDULE_ID, -1L)
        when {
            groupId > 0 -> pendingOpen.open(OpenTarget.Chat(groupId))
            scheduleId > 0 -> pendingOpen.open(OpenTarget.Schedule(scheduleId))
            intent.getBooleanExtra(PushNotifier.EXTRA_DATA_EXPORT, false) -> pendingOpen.open(OpenTarget.DataExport)
            intent.getBooleanExtra(PushNotifier.EXTRA_SESSIONS, false) -> pendingOpen.open(OpenTarget.Sessions)
        }
        intent.removeExtra(PushNotifier.EXTRA_GROUP_ID)
        intent.removeExtra(PushNotifier.EXTRA_SCHEDULE_ID)
        intent.removeExtra(PushNotifier.EXTRA_DATA_EXPORT)
        intent.removeExtra(PushNotifier.EXTRA_SESSIONS)
    }
}
