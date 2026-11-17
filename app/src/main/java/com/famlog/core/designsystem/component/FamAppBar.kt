package com.famlog.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/**
 * Figma App Bar (`3:107`): `[Title Group = Title + chevron-down 20] [Spacer] [Action]`.
 * [onGroupSwitcherClick]이 있으면 제목 옆에 ▾를 보이고 누를 수 있게 한다 (SCR-70).
 */
@Composable
fun FamAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onGroupSwitcherClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Surface(color = FamTheme.colors.bgDefault, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = FamDimens.appBarHeight)
                .padding(horizontal = FamDimens.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
        ) {
            val switcherLabel = stringResource(R.string.app_bar_group_switcher)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FamDimens.xs),
                modifier = Modifier
                    .heightIn(min = FamDimens.minTouchTarget)
                    .then(
                        if (onGroupSwitcherClick != null) {
                            Modifier.clickable(
                                onClickLabel = switcherLabel,
                                role = Role.Button,
                                onClick = onGroupSwitcherClick,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    text = title,
                    style = FamTheme.type.titleMedium,
                    color = FamTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                if (onGroupSwitcherClick != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = FamTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}
