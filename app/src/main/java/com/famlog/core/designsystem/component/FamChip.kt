package com.famlog.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/**
 * Figma Member Chip (`3:67`). [dotColor]가 있으면 이름 앞에 구성원 색 점 8px (캘린더 필터·참여자).
 * radius full, padding 4/12.
 * 선택 = brand/subtle + 1px brand + brand 글자 · 비선택 = bg/default + 1px border/default + text/secondary.
 * 터치 영역은 48dp를 보장한다.
 */
@Composable
fun FamChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    /** 여러 개를 고르는 칩(구성원 필터·참여자)은 Checkbox */
    role: Role = Role.RadioButton,
) {
    val colors = FamTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = FamDimens.minTouchTarget)
            .selectable(selected = selected, role = role, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) colors.brandSubtle else colors.bgDefault,
            border = BorderStroke(1.dp, if (selected) colors.brandPrimary else colors.borderDefault),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = FamDimens.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (dotColor != null) Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Text(
                    text = text,
                    style = FamTheme.type.label,
                    color = if (selected) colors.brandPrimary else colors.textSecondary,
                )
            }
        }
    }
}
