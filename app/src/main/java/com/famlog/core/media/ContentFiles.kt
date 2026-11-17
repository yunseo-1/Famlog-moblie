package com.famlog.core.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.famlog.core.data.LocalFileSource
import com.famlog.core.model.LocalMedia
import com.famlog.core.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Photo Picker 파일을 그대로 흘려 올린다 (앱 저장소에 복사하지 않는다) */
@Singleton
class ContentFileSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalFileSource {

    override fun requestBody(uri: String, mime: String, size: Long): RequestBody {
        val parsed = Uri.parse(uri)
        // 지금 읽을 수 있는지 먼저 확인한다 (권한이 사라졌으면 다시 해도 안 된다)
        try {
            context.contentResolver.openInputStream(parsed)?.close() ?: throw FileNotFoundException()
        } catch (e: SecurityException) {
            throw IOException("파일 접근 권한 없음", e)
        }
        return object : RequestBody() {
            override fun contentType(): MediaType = mime.toMediaType()
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                val input = context.contentResolver.openInputStream(parsed) ?: throw FileNotFoundException()
                input.source().use { sink.writeAll(it) }
            }
        }
    }
}

/** 고른 파일 검사 결과. [skipped]는 형식·크기·길이 때문에 뺀 개수 */
data class PickedMedia(val items: List<LocalMedia>, val skipped: Int)

interface MediaInspector {
    suspend fun inspect(uris: List<String>): PickedMedia
}

/**
 * 서버와 같은 기준으로 먼저 걸러 낸다 (백엔드 famlog.media: 사진 jpeg·png·webp·heic·heif 30MB, 동영상 mp4·mov 500MB·5분).
 * 업로드가 앱 재시작 뒤에도 이어지도록 Photo Picker 읽기 권한을 유지해 둔다.
 */
@Singleton
class ContentMediaInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaInspector {

    override suspend fun inspect(uris: List<String>): PickedMedia = withContext(Dispatchers.IO) {
        val items = uris.mapNotNull { inspectOne(Uri.parse(it)) }
        PickedMedia(items, skipped = uris.size - items.size)
    }

    private fun inspectOne(uri: Uri): LocalMedia? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase() ?: return null
        val kind = when (mime) {
            in IMAGE_MIMES -> MediaKind.IMAGE
            in VIDEO_MIMES -> MediaKind.VIDEO
            else -> return null
        }
        val size = sizeOf(resolver, uri) ?: return null
        val maxSize = if (kind == MediaKind.IMAGE) MAX_IMAGE_BYTES else MAX_VIDEO_BYTES
        if (size <= 0 || size > maxSize) return null
        val duration = if (kind == MediaKind.VIDEO) durationSec(uri) ?: return null else null
        if (duration != null && duration > MAX_VIDEO_SECONDS) return null

        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // 유지 권한을 못 받아도 지금은 읽을 수 있다. 앱이 꺼지면 업로드가 실패할 수 있다
        }
        return LocalMedia(uri.toString(), mime, size, kind, duration)
    }

    private fun sizeOf(resolver: ContentResolver, uri: Uri): Long? =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    private fun durationSec(uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.let { ((it + 999) / 1000).toInt() }
        } catch (e: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        val IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
        val VIDEO_MIMES = setOf("video/mp4", "video/quicktime")
        const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        const val MAX_VIDEO_BYTES = 500L * 1024 * 1024
        const val MAX_VIDEO_SECONDS = 300
    }
}
