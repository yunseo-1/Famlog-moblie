package com.famlog.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

/** 디버그 요청 로그: Presigned URL 서명이 로그에 남지 않는다 (src/debug BuildTypeNetworkModule) */
class DebugLogRedactionTest {

    @Test
    fun 쿼리_문자열은_지운다() {
        val line = "<-- 200 OK http://10.0.2.2:8333/famlog-media/media/a/thumb_s.webp?X-Amz-Signature=abc&X-Amz-Credential=k (215ms)"

        assertEquals(
            "<-- 200 OK http://10.0.2.2:8333/famlog-media/media/a/thumb_s.webp?… (215ms)",
            BuildTypeNetworkModule.redactQuery(line),
        )
    }

    @Test
    fun 쿼리가_없으면_그대로다() {
        val line = "--> GET http://10.0.2.2:8080/api/v1/groups/me"

        assertEquals(line, BuildTypeNetworkModule.redactQuery(line))
    }
}
