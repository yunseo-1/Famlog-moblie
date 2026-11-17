package com.famlog.core.network.stomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StompFrameTest {

    @Test
    fun SEND_프레임은_헤더_빈줄_본문_NULL_순서로_만든다() {
        val frame = StompFrame("SEND", linkedMapOf("destination" to "/pub/groups/1/messages"), """{"a":1}""")

        assertEquals("SEND\ndestination:/pub/groups/1/messages\n\n{\"a\":1}\u0000", frame.encode())
    }

    @Test
    fun CONNECT_외_프레임의_헤더_값은_이스케이프한다() {
        val encoded = StompFrame("SEND", mapOf("x" to "a:b\nc\\d")).encode()
        assertEquals("SEND\nx:a\\cb\\nc\\\\d\n\n\u0000", encoded)

        // CONNECT는 1.2 규칙상 이스케이프하지 않는다 (Authorization 값의 공백·콜론 그대로)
        val connect = StompFrame("CONNECT", mapOf("Authorization" to "Bearer a:b")).encode()
        assertEquals("CONNECT\nAuthorization:Bearer a:b\n\n\u0000", connect)
    }

    @Test
    fun MESSAGE_프레임을_읽고_헤더_이스케이프를_푼다() {
        val text = "MESSAGE\ndestination:/sub/groups/1\nsubscription:g-1\nx:a\\cb\ncontent-length:9\n\n{\"k\":\"v\"}\u0000"

        val frame = StompFrame.decode(text)!!

        assertEquals("MESSAGE", frame.command)
        assertEquals("/sub/groups/1", frame.header("destination"))
        assertEquals("a:b", frame.header("x"))
        assertEquals("{\"k\":\"v\"}", frame.body)
    }

    @Test
    fun 줄바꿈만_온_하트비트는_null() {
        assertNull(StompFrame.decode("\n"))
        assertNull(StompFrame.decode("\r\n"))
    }

    @Test
    fun ERROR_프레임의_message_헤더가_에러_코드다() {
        val frame = StompFrame.decode("ERROR\nmessage:AUTH_TOKEN_EXPIRED\n\n{\"code\":\"AUTH_TOKEN_EXPIRED\"}\u0000")!!

        assertEquals("AUTH_TOKEN_EXPIRED", frame.header("message"))
    }

    @Test
    fun toString에는_헤더_값을_넣지_않는다() {
        val frame = StompFrame("CONNECT", mapOf("Authorization" to "Bearer secret-token"))

        assertFalse("secret-token" in frame.toString())
    }
}
