package com.famlog.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.famlog.core.designsystem.theme.FamTheme

/** Figma Bottom Nav (`3:77`): 탭 5개 고정. 선택 탭은 brand/primary, 나머지는 text/tertiary */
@Composable
fun FamBottomNav(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = FamTheme.colors.bgDefault,
        content = content,
    )
}

@Composable
fun RowScope.FamBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painter = icon, contentDescription = null) },
        label = { Text(text = label, style = FamTheme.type.caption) },
        modifier = modifier,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = FamTheme.colors.brandPrimary,
            selectedTextColor = FamTheme.colors.brandPrimary,
            indicatorColor = FamTheme.colors.bgDefault,
            unselectedIconColor = FamTheme.colors.textTertiary,
            unselectedTextColor = FamTheme.colors.textTertiary,
        ),
    )
}
