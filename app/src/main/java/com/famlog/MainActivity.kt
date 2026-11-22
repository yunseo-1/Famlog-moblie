package com.famlog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.push.PushNotifier
import com.famlog.debug.BuildTypeSetup
import com.famlog.navigation.FamlogNavHost
import com.famlog.navigation.OpenTarget
import com.famlog.navigation.PendingOpen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pendingOpen: PendingOpen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BuildTypeSetup.onCreate(this)
        // 화면 회전 등으로 다시 만들어질 때는 같은 알림 탭을 다시 처리하지 않는다
        if (savedInstanceState == null) handleNotificationTap(intent)
        setContent {
            FamLogTheme {
                FamlogNavHost()
            }
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
        }
        intent.removeExtra(PushNotifier.EXTRA_GROUP_ID)
        intent.removeExtra(PushNotifier.EXTRA_SCHEDULE_ID)
        intent.removeExtra(PushNotifier.EXTRA_DATA_EXPORT)
    }
}
