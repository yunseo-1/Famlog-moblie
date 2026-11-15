package com.famlog.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.famlog.R
import com.famlog.core.designsystem.component.PlaceholderScreen

/** 세션이 끝났을 때 이동하는 자리. 카카오·Google 로그인(SCR-01)은 S7에서 만든다 */
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.login_placeholder_title),
        body = stringResource(R.string.login_placeholder_body),
        modifier = modifier,
    )
}
