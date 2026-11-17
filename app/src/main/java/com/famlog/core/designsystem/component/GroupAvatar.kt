package com.famlog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.famlog.core.designsystem.theme.FamTheme

/**
 * 그룹·구성원 아바타 (Figma 07의 40px 원). 사진 업로드(S9) 전까지는 이름 첫 글자를 보인다.
 * 이름은 옆에 글자로 함께 보이므로 접근성 설명은 넣지 않는다.
 */
@Composable
fun GroupAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(FamTheme.colors.surfacePlaceholder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1),
            style = FamTheme.type.bodyMediumStrong,
            color = FamTheme.colors.textSecondary,
        )
    }
}
