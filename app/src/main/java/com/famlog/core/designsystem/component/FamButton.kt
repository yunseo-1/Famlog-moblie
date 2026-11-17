package com.famlog.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

enum class FamButtonStyle { PRIMARY, SECONDARY, GHOST }

enum class FamButtonSize { MEDIUM, SMALL }

/**
 * Figma Button (`3:60`). Medium padding 12/20 · Small 8/12, radius 12.
 * Primary = brand/primary + on-brand · Secondary = bg/default + 1px border/strong · Ghost = 배경 없음 + brand/primary.
 * [loading]이면 누를 수 없고 글자 대신 진행 표시를 보인다.
 */
@Composable
fun FamButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FamButtonStyle = FamButtonStyle.PRIMARY,
    size: FamButtonSize = FamButtonSize.MEDIUM,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = FamTheme.colors
    val shape = RoundedCornerShape(FamDimens.radiusMd)
    val padding = when (size) {
        FamButtonSize.MEDIUM -> PaddingValues(horizontal = FamDimens.xl, vertical = FamDimens.md)
        FamButtonSize.SMALL -> PaddingValues(horizontal = FamDimens.md, vertical = FamDimens.sm)
    }
    val textStyle = when (size) {
        FamButtonSize.MEDIUM -> FamTheme.type.bodyMediumStrong
        FamButtonSize.SMALL -> FamTheme.type.label
    }
    val sizedModifier = modifier.heightIn(min = FamDimens.minTouchTarget)
    val label: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = if (style == FamButtonStyle.PRIMARY) colors.textOnBrand else colors.brandPrimary,
            )
        } else {
            Text(text = text, style = textStyle, textAlign = TextAlign.Center)
        }
    }
    val clickable = enabled && !loading
    when (style) {
        FamButtonStyle.PRIMARY -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = clickable,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.brandPrimary,
                contentColor = colors.textOnBrand,
                // 진행 중에는 비활성 회색 대신 브랜드 색을 유지한다
                disabledContainerColor = if (loading) colors.brandPrimary else colors.surfaceMuted,
                disabledContentColor = if (loading) colors.textOnBrand else colors.textTertiary,
            ),
        ) { label() }

        FamButtonStyle.SECONDARY -> OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = clickable,
            shape = shape,
            contentPadding = padding,
            border = BorderStroke(1.dp, if (clickable) colors.borderStrong else colors.borderDefault),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.bgDefault,
                contentColor = colors.textPrimary,
                disabledContainerColor = colors.bgDefault,
                disabledContentColor = colors.textTertiary,
            ),
        ) { label() }

        FamButtonStyle.GHOST -> TextButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = clickable,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.brandPrimary,
                disabledContentColor = colors.textTertiary,
            ),
        ) { label() }
    }
}
