package com.famlog.core.network

import com.famlog.core.model.ChatMedia
import com.famlog.core.model.MediaKind
import com.famlog.core.model.MediaStatus
import kotlinx.serialization.Serializable

/** 백엔드 MediaDtos와 같은 모양 (사양서 11.3 CHAT-05) */
@Serializable
data class UploadUrlsRequest(val groupId: Long, val items: List<UploadItem>)

@Serializable
data class UploadItem(val mimeType: String, val size: Long)

@Serializable
data class UploadUrlsResponse(val items: List<UploadTarget>)

/** [uploadUrl]에 [headers]를 그대로 넣어 PUT 한다 (서명에 들어 있다). URL에 서명이 있으니 로그에 남기지 않는다 */
@Serializable
data class UploadTarget(
    val mediaId: Long,
    val uploadUrl: String,
    val method: String = "PUT",
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: String? = null,
) {
    override fun toString(): String = "UploadTarget(mediaId=$mediaId)"
}

@Serializable
data class MediaResponse(
    val id: Long,
    val mediaType: String,
    val mime: String? = null,
    val size: Long = 0,
    val status: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationSec: Int? = null,
    val takenAt: String? = null,
)

/** 그룹 이벤트 media.updated: 처리 끝(READY)·실패 */
@Serializable
data class MediaUpdated(val groupId: Long, val messageId: Long, val media: MediaResponse)

enum class MediaVariant { ORIGINAL, THUMB_S, THUMB_M, THUMB_L }

fun MediaResponse.toModel() = ChatMedia(
    id = id,
    kind = if (mediaType == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.IMAGE,
    status = MediaStatus.of(status),
    width = width,
    height = height,
    durationSec = durationSec,
)
