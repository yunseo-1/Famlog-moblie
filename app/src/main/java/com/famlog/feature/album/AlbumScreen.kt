package com.famlog.feature.album

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.famlog.R
import com.famlog.core.designsystem.component.PlaceholderScreen

/** v1.0에서는 "곧 만나요" 안내만 보인다 (사양서 3.1) */
@Composable
fun AlbumScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.coming_soon_title),
        body = stringResource(R.string.coming_soon_album),
        modifier = modifier,
    )
}
