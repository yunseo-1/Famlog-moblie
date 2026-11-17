package com.famlog.core.data

import com.famlog.core.model.LocalMedia
import com.famlog.core.model.MediaKind
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import java.io.IOException

/** 업로드를 백그라운드 작업으로 맡긴다 (WorkManager). 같은 메시지는 한 작업만 돈다 */
interface UploadScheduler {
    fun schedule(messageKey: String)
    fun cancel(messageKey: String)
}

/** Photo Picker가 준 파일을 읽는다. 권한이 사라졌거나 파일이 없으면 [IOException] */
interface LocalFileSource {
    @Throws(IOException::class)
    fun requestBody(uri: String, mime: String, size: Long): RequestBody
}

/** Room에 저장하는 [LocalMedia] */
@Serializable
internal data class LocalMediaJson(
    val uri: String,
    val mime: String,
    val size: Long,
    val kind: String,
    val durationSec: Int? = null,
    val mediaId: Long? = null,
)

internal fun LocalMedia.toJson() = LocalMediaJson(uri, mime, size, kind.name, durationSec, mediaId)

internal fun LocalMediaJson.toModel() = LocalMedia(
    uri = uri,
    mime = mime,
    size = size,
    kind = if (kind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.IMAGE,
    durationSec = durationSec,
    mediaId = mediaId,
)
