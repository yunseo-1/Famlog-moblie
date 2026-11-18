package com.famlog.core.push

import com.famlog.core.data.PushReceiver
import com.famlog.core.data.PushTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** FCM 수신 (NOTI-01). 앱이 꺼져 있어도 data 메시지(우선순위 HIGH)는 여기로 온다 */
@AndroidEntryPoint
class FamlogMessagingService : FirebaseMessagingService() {

    @Inject lateinit var receiver: PushReceiver

    @Inject lateinit var notifier: PushNotifier

    @Inject lateinit var pushTokens: PushTokenRepository

    /** FCM 작업 스레드에서 불린다. 저장소만 읽어 곧 끝나므로 이 스레드를 잠시 막는다 */
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = runBlocking { receiver.toNotification(message.data) } ?: return
        notifier.show(notification)
    }

    override fun onNewToken(token: String) {
        pushTokens.registerInBackground(token)
    }
}
