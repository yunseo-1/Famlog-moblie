package com.famlog.core.model

/** 서버 호출 결과. 화면은 [Failure.error]의 code로 분기하고 message를 그대로 보여줄 수 있다 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** 서버가 에러로 응답했다. [error]가 null이면 공통 에러 형식이 아닌 응답 */
    data class Failure(val status: Int, val error: ApiError?) : ApiResult<Nothing>

    /** 연결 실패·시간 초과 */
    data object NetworkError : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
    ApiResult.NetworkError -> ApiResult.NetworkError
}

val ApiResult<*>.errorCode: String? get() = (this as? ApiResult.Failure)?.error?.code
