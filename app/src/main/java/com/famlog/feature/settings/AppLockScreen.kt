package com.famlog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.famlog.R
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/** 앱 잠금 화면 (SEC-01). 앱 위를 덮고 아래 화면의 터치를 막는다. 뜨면 MainActivity가 바로 기기 인증을 띄운다 */
@Composable
fun AppLockContent(onUnlock: () -> Unit) {
    val colors = FamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgDefault)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(FamDimens.screenPadding)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.app_name),
            style = FamTheme.type.titleLarge,
            color = colors.brandPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(FamDimens.sm))
        Text(stringResource(R.string.app_lock_locked), style = FamTheme.type.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        FamButton(text = stringResource(R.string.app_lock_unlock), onClick = onUnlock, modifier = Modifier.fillMaxWidth())
    }
}
