package com.famlog.core.push

import com.famlog.core.data.ChatPresence
import com.famlog.core.data.PushTokenSource
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

class FirebasePushTokenSource @Inject constructor() : PushTokenSource {
    override suspend fun token(): String? = try {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    } catch (e: IllegalStateException) {
        // Firebase가 초기화되지 않았다 (google-services.json 없음)
        null
    }
}

/** 보이는 채팅방은 하나뿐이다. 다른 방으로 바로 넘어가도 이전 방의 leave가 새 방을 지우지 않게 비교한다 */
@Singleton
class NotificationChatPresence @Inject constructor(private val notifier: PushNotifier) : ChatPresence {
    @Volatile private var visible: Long? = null

    override val visibleGroupId: Long? get() = visible

    override fun enter(groupId: Long) {
        synchronized(this) { visible = groupId }
        notifier.cancelChat(groupId)
    }

    override fun leave(groupId: Long) {
        synchronized(this) { if (visible == groupId) visible = null }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    abstract fun bindPushTokenSource(impl: FirebasePushTokenSource): PushTokenSource

    @Binds
    abstract fun bindChatPresence(impl: NotificationChatPresence): ChatPresence
}
