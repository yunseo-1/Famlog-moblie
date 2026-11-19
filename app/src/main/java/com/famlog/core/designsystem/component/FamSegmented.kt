package com.famlog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.famlog.core.designsystem.theme.FamTheme

/**
 * Figma Segmented: bg surface/muted, radius full, padding 4. 선택 칸 bg/default + Body/Medium Strong.
 * 캘린더 [일정 | 할 일], 장소 [지도 | 리스트]
 */
@Composable
fun <T> FamSegmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted, CircleShape)
            .padding(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(if (isSelected) colors.bgDefault else colors.surfaceMuted, CircleShape)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = if (isSelected) FamTheme.type.bodyMediumStrong else FamTheme.type.bodyMedium,
                    color = if (isSelected) colors.textPrimary else colors.textTertiary,
                )
            }
        }
    }
}
