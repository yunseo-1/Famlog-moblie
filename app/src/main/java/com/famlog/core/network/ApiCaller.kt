package com.famlog.core.network

import com.famlog.core.model.ApiResult
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Retrofit 호출을 [ApiResult]로 감싼다. 에러 본문은 { code, message, traceId }로 읽는다 */
@Singleton
class ApiCaller @Inject constructor(private val errorParser: ApiErrorParser) {

    suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        ApiResult.Failure(e.code(), errorParser.parse(e.response()?.errorBody()?.string()))
    } catch (e: IOException) {
        ApiResult.NetworkError
    } catch (e: SerializationException) {
        ApiResult.Failure(status = -1, error = null)
    }
}
