package com.famlog.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/** 아직 만들지 않은 화면과 "곧 만나요" 안내에 쓰는 가운데 정렬 안내문 */
@Composable
fun PlaceholderScreen(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FamDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(FamDimens.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(text = title, style = FamTheme.type.titleMedium, color = FamTheme.colors.textPrimary)
        }
        Text(
            text = body,
            style = FamTheme.type.bodyMedium,
            color = FamTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
