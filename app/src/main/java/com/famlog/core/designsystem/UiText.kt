package com.famlog.core.designsystem

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.famlog.R
import com.famlog.core.model.ApiResult

/** ViewModel이 화면에 보낼 문구. 앱 문구는 리소스 ID, 서버 에러 message는 글자 그대로 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plain -> text
}

/** 실패한 서버 호출을 안내 문구로. 서버 message는 사용자에게 보여주도록 쓰여 있다 (사양서 11.1) */
fun ApiResult<*>.toUiText(): UiText = when (this) {
    ApiResult.NetworkError -> UiText.Res(R.string.error_network)
    is ApiResult.Failure -> error?.message?.let(UiText::Plain) ?: UiText.Res(R.string.error_unknown)
    is ApiResult.Success -> UiText.Res(R.string.error_unknown)
}

/** 스낵바처럼 Composable 밖에서 문구가 필요할 때 */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.toTypedArray())
    is UiText.Plain -> text
}
