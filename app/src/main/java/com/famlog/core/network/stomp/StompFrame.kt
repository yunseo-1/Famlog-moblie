package com.famlog.core.network.stomp

/**
 * STOMP 1.2 프레임 (https://stomp.github.io/stomp-specification-1.2.html).
 * `COMMAND\n header:value\n ... \n\n body \0`. 헤더 값의 `\` `\n` `:` `\r`은 이스케이프한다 (CONNECT·CONNECTED 제외).
 */
data class StompFrame(
    val command: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
) {
    fun header(name: String): String? = headers[name]

    /** 토큰이 들어 있을 수 있어 헤더 값은 보이지 않는다 */
    override fun toString(): String = "StompFrame($command, headers=${headers.keys})"

    fun encode(): String = buildString {
        append(command).append('\n')
        val escape = command != CONNECT
        headers.forEach { (name, value) ->
            append(if (escape) escapeHeader(name) else name)
            append(':')
            append(if (escape) escapeHeader(value) else value)
            append('\n')
        }
        append('\n')
        append(body)
        append(NULL)
    }

    companion object {
        const val CONNECT = "CONNECT"
        const val CONNECTED = "CONNECTED"
        const val SUBSCRIBE = "SUBSCRIBE"
        const val UNSUBSCRIBE = "UNSUBSCRIBE"
        const val SEND = "SEND"
        const val MESSAGE = "MESSAGE"
        const val ERROR = "ERROR"
        const val DISCONNECT = "DISCONNECT"

        private const val NULL = '\u0000'

        /**
         * 웹소켓 메시지 하나를 프레임으로 읽는다. 하트비트(줄바꿈만 온 경우)면 null.
         * 서버는 메시지 하나에 프레임 하나만 보낸다.
         */
        fun decode(text: String): StompFrame? {
            val trimmed = text.trimStart('\n', '\r')
            if (trimmed.isEmpty() || trimmed.all { it == NULL }) return null

            val headerEnd = trimmed.indexOf("\n\n").takeIf { it >= 0 }
                ?: trimmed.indexOf("\r\n\r\n").takeIf { it >= 0 }
                ?: throw IllegalArgumentException("STOMP 프레임에 헤더 끝이 없다")
            val separatorLength = if (trimmed.startsWith("\n\n", headerEnd)) 2 else 4
            val lines = trimmed.substring(0, headerEnd).split('\n').map { it.removeSuffix("\r") }
            val command = lines.first()
            val unescape = command != CONNECTED
            val headers = LinkedHashMap<String, String>()
            lines.drop(1).forEach { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) return@forEach
                val name = line.substring(0, colon).let { if (unescape) unescapeHeader(it) else it }
                val value = line.substring(colon + 1).let { if (unescape) unescapeHeader(it) else it }
                // 같은 헤더가 여러 번 오면 처음 값을 쓴다 (1.2 규칙)
                headers.putIfAbsent(name, value)
            }

            // 본문은 JSON 텍스트라 NULL 문자가 들어 있지 않다. content-length 대신 NULL까지 읽는다
            val body = trimmed.substring(headerEnd + separatorLength).substringBefore(NULL)
            return StompFrame(command, headers, body)
        }

        private fun escapeHeader(value: String): String = buildString {
            value.forEach { c ->
                when (c) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    ':' -> append("\\c")
                    else -> append(c)
                }
            }
        }

        private fun unescapeHeader(value: String): String {
            if ('\\' !in value) return value
            val out = StringBuilder()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        '\\' -> out.append('\\')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        'c' -> out.append(':')
                        else -> out.append(c).append(value[i + 1])
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
