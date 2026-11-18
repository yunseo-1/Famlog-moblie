package com.famlog.core.data

import com.famlog.core.datastore.FakeTokenStore
import com.famlog.core.model.AuthTokens
import com.famlog.core.model.PushMessage
import com.famlog.testing.FakeSessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PushReceiverTest {

    private val tokenStore = FakeTokenStore(AuthTokens("access", "refresh"))
    private val presence = object : ChatPresence {
        override var visibleGroupId: Long? = null
        override fun enter(groupId: Long) { visibleGroupId = groupId }
        override fun leave(groupId: Long) { if (visibleGroupId == groupId) visibleGroupId = null }
    }
    private val receiver = PushReceiver(tokenStore, FakeSessionStore(userId = ME), presence)

    private fun chat(groupId: Long = GROUP, senderId: Long? = OTHER) = buildMap {
        put("type", "CHAT_MESSAGE")
        put("channel", "CHAT")
        put("groupId", groupId.toString())
        put("groupName", "우리 가족")
        put("messageId", "1")
        put("messageType", "TEXT")
        if (senderId != null) put("senderId", senderId.toString())
    }

    @Test
    fun 다른_사람의_새_메시지는_띄운다() = runTest {
        assertNotNull(receiver.toNotification(chat()))
    }

    @Test
    fun 보고_있는_채팅방의_메시지는_띄우지_않는다() = runTest {
        presence.enter(GROUP)

        assertNull(receiver.toNotification(chat()))
        assertNotNull(receiver.toNotification(chat(groupId = 99)))
    }

    @Test
    fun 채팅방을_나오면_다시_띄운다() = runTest {
        presence.enter(GROUP)
        presence.leave(GROUP)

        assertNotNull(receiver.toNotification(chat()))
    }

    @Test
    fun 내가_보낸_메시지는_띄우지_않는다() = runTest {
        assertNull(receiver.toNotification(chat(senderId = ME)))
    }

    @Test
    fun 로그아웃했으면_띄우지_않는다() = runTest {
        tokenStore.clear()

        assertNull(receiver.toNotification(chat()))
    }

    @Test
    fun 보고_있는_방이어도_내보내짐은_띄운다() = runTest {
        presence.enter(GROUP)
        val data = mapOf("type" to PushMessage.TYPE_MEMBER_REMOVED, "groupId" to GROUP.toString(), "groupName" to "우리 가족")

        assertNotNull(receiver.toNotification(data))
    }

    private companion object {
        const val GROUP = 7L
        const val ME = 1L
        const val OTHER = 2L
    }
}
