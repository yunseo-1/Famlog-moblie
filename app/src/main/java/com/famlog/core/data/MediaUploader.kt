package com.famlog.core.data

import com.famlog.core.database.MessageDao
import com.famlog.core.model.ApiResult
import com.famlog.core.model.SendState
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.MediaApi
import com.famlog.core.network.UploadClient
import com.famlog.core.network.UploadItem
import com.famlog.core.network.UploadUrlsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class UploadOutcome {
    /** 다 올렸다(또는 할 일이 없다). 메시지는 [ChatRepository.sendIfReady]가 보낸다 */
    DONE,

    /** 네트워크·서버 문제. 잠시 뒤 다시 */
    RETRY,

    /** 다시 해도 안 된다 (형식 거부, 파일 접근 불가). 메시지는 FAILED */
    FAILED,
}

/**
 * 사진·동영상 메시지 하나의 파일을 올린다 (CHAT-05, 백엔드 S9 순서).
 * 1) POST /media/upload-urls (아직 안 올린 파일만, 시도마다 새 URL — 10분 만료)
 * 2) 받은 headers 그대로 PUT  3) POST /media/{id}/complete → 파일마다 mediaId를 Room에 남겨 다음 시도에서 건너뛴다.
 * 다 올리면 메시지를 보낸다. 파일 주소·서명 URL은 로그에 남기지 않는다.
 */
@Singleton
class MediaUploader @Inject constructor(
    private val api: MediaApi,
    private val caller: ApiCaller,
    private val dao: MessageDao,
    private val files: LocalFileSource,
    @UploadClient private val uploadClient: OkHttpClient,
    private val chatRepository: ChatRepository,
    private val json: Json,
) {
    suspend fun upload(messageKey: String): UploadOutcome {
        val entity = dao.get(messageKey) ?: return UploadOutcome.DONE
        if (entity.seq != null) return UploadOutcome.DONE
        val stored = entity.localMediaJson ?: return UploadOutcome.DONE
        val items = json.decodeFromString(SERIALIZER, stored).toMutableList()

        val todo = items.indices.filter { items[it].mediaId == null }
        if (todo.isNotEmpty()) {
            val issued = caller.call {
                api.uploadUrls(UploadUrlsRequest(entity.groupId, todo.map { UploadItem(items[it].mime, items[it].size) }))
            }
            val targets = when (issued) {
                is ApiResult.Success -> issued.value.items
                else -> return fail(messageKey, issued)
            }
            for ((index, target) in todo.zip(targets)) {
                val item = items[index]
                val body = try {
                    files.requestBody(item.uri, item.mime, item.size)
                } catch (e: IOException) {
                    return markFailed(messageKey)
                }
                val request = Request.Builder()
                    .url(target.uploadUrl)
                    .method(target.method, body)
                    .apply { target.headers.forEach { (name, value) -> header(name, value) } }
                    .build()
                val uploaded = withContext(Dispatchers.IO) {
                    try {
                        uploadClient.newCall(request).execute().use { it.isSuccessful }
                    } catch (e: IOException) {
                        false
                    }
                }
                if (!uploaded) return UploadOutcome.RETRY

                val completed = caller.call { api.complete(target.mediaId) }
                if (completed !is ApiResult.Success) return fail(messageKey, completed)
                items[index] = item.copy(mediaId = target.mediaId)
                dao.updateLocalMedia(messageKey, json.encodeToString(SERIALIZER, items))
            }
        }
        chatRepository.sendIfReady(messageKey)
        return UploadOutcome.DONE
    }

    /** 4xx(전송 제한 제외)는 다시 해도 같다 → FAILED. 나머지는 다시 */
    private suspend fun fail(messageKey: String, result: ApiResult<*>): UploadOutcome {
        val status = (result as? ApiResult.Failure)?.status ?: return UploadOutcome.RETRY
        return if (status in 400..499 && status != 401 && status != 429) markFailed(messageKey) else UploadOutcome.RETRY
    }

    private suspend fun markFailed(messageKey: String): UploadOutcome {
        dao.updateSendState(messageKey, SendState.FAILED.name)
        return UploadOutcome.FAILED
    }

    private companion object {
        val SERIALIZER = ListSerializer(LocalMediaJson.serializer())
    }
}
