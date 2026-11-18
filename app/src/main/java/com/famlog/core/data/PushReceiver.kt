package com.famlog.core.data

import com.famlog.core.datastore.SessionStore
import com.famlog.core.datastore.TokenStore
import com.famlog.core.model.PushMessage
import javax.inject.Inject
import javax.inject.Singleton

/** 지금 화면에 보이는 채팅방. 그 방의 새 메시지는 STOMP로 이미 받았으니 알림을 띄우지 않는다 */
interface ChatPresence {
    val visibleGroupId: Long?

    /** 채팅방이 보이기 시작했다. 그 그룹에 쌓인 알림도 지운다 */
    fun enter(groupId: Long)
    fun leave(groupId: Long)
}

/** FCM data 메시지를 받아 띄울 알림을 고른다 (NOTI-01). 띄우지 않을 거면 null */
@Singleton
class PushReceiver @Inject constructor(
    private val tokenStore: TokenStore,
    private val sessionStore: SessionStore,
    private val presence: ChatPresence,
) {

    suspend fun toNotification(data: Map<String, String>): PushMessage? {
        val message = PushMessage.parse(data) ?: return null
        // 로그아웃하면 서버가 토큰을 지우지만, 그 사이에 도착한 것은 띄우지 않는다
        if (tokenStore.get() == null) return null
        if (message is PushMessage.Chat) {
            if (message.groupId == presence.visibleGroupId) return null
            val me = sessionStore.userId()
            if (me != null && message.senderId == me) return null
        }
        return message
    }
}
