package com.famlog.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사진·동영상 내려받기 주소 (GET /media/{id}/content?variant=). 서버가 10분짜리 저장소 주소로 302를 준다.
 * 이미지 로더는 앱 OkHttp로 이 주소를 불러 Authorization을 붙이고, 다른 호스트로 넘어갈 때 OkHttp가 그 헤더를 뗀다.
 */
@Singleton
class MediaUrls @Inject constructor(
    @ApiBaseUrl private val baseUrl: String,
    client: OkHttpClient,
) {
    private val noRedirectClient = client.newBuilder().followRedirects(false).build()

    fun content(mediaId: Long, variant: MediaVariant): String = contentUrl(baseUrl, mediaId, variant)

    /**
     * 동영상 플레이어(VideoView)는 인증 헤더를 넣을 수 없어서, 302의 저장소 주소를 먼저 받아 넘긴다.
     * 주소에 서명이 있으니 로그에 남기지 않는다. 실패하면 null.
     */
    fun resolve(mediaId: Long, variant: MediaVariant): String? = try {
        noRedirectClient.newCall(Request.Builder().url(content(mediaId, variant)).build()).execute().use { response ->
            if (response.isRedirect) response.header("Location") else null
        }
    } catch (e: IOException) {
        null
    }

    companion object {
        fun contentUrl(baseUrl: String, mediaId: Long, variant: MediaVariant): String =
            "${baseUrl}media/$mediaId/content?variant=${variant.name}"
    }
}
