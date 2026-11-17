package com.famlog.core.network

import com.famlog.core.model.ApiError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** 에러 응답 본문을 { code, message, traceId }로 읽는다. 형식이 다르면 null */
@Singleton
class ApiErrorParser @Inject constructor(private val json: Json) {

    fun parse(body: String?): ApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            json.decodeFromString<ErrorResponse>(body).let { ApiError(it.code, it.message, it.traceId) }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** 응답 본문을 소비하지 않고 읽는다 (Authenticator에서 사용) */
    fun peek(response: Response): ApiError? = parse(response.peekBody(MAX_ERROR_BODY_BYTES).string())

    private companion object {
        const val MAX_ERROR_BODY_BYTES = 16 * 1024L
    }
}
