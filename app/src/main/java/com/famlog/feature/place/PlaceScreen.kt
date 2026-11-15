package com.famlog.feature.place

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.famlog.R
import com.famlog.core.designsystem.component.PlaceholderScreen

/** 빈 화면. 실제 화면은 사양서 20.4 단계에서 만든다 */
@Composable
fun PlaceScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(body = stringResource(R.string.placeholder_place), modifier = modifier)
}
