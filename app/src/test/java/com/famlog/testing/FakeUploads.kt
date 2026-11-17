package com.famlog.testing

import com.famlog.core.data.LocalFileSource
import com.famlog.core.data.UploadScheduler
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class FakeUploadScheduler : UploadScheduler {
    val scheduled = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override fun schedule(messageKey: String) {
        scheduled += messageKey
    }

    override fun cancel(messageKey: String) {
        cancelled += messageKey
    }
}

/** 파일 내용 대신 "uri의 바이트"를 보낸다. [unreadable]에 든 uri는 권한이 사라진 것처럼 실패한다 */
class FakeFileSource(private val unreadable: Set<String> = emptySet()) : LocalFileSource {
    override fun requestBody(uri: String, mime: String, size: Long): RequestBody {
        if (uri in unreadable) throw IOException("권한 없음")
        return uri.toByteArray().toRequestBody(mime.toMediaType())
    }
}
