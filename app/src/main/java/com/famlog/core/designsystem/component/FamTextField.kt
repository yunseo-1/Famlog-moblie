package com.famlog.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/**
 * 한 줄 입력칸. Figma에 입력 컴포넌트가 아직 없어 Material 3 OutlinedTextField를 토큰 색으로 맞췄다.
 * [supportingText]는 안내·글자 수, [errorText]가 있으면 그 문구를 오류 색으로 대신 보인다.
 */
@Composable
fun FamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** 메모처럼 여러 줄이면 [minLines] 이상으로 늘어난다 */
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val colors = FamTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = FamTheme.type.bodyLarge,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.textTertiary) } },
        supportingText = (errorText ?: supportingText)?.let { { Text(it, style = FamTheme.type.caption) } },
        isError = errorText != null,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(FamDimens.radiusMd),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.brandPrimary,
            unfocusedBorderColor = colors.borderStrong,
            focusedLabelColor = colors.brandPrimary,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.brandPrimary,
            errorBorderColor = colors.statusDanger,
            errorLabelColor = colors.statusDanger,
            errorSupportingTextColor = colors.statusDanger,
            unfocusedSupportingTextColor = colors.textTertiary,
            focusedSupportingTextColor = colors.textTertiary,
        ),
    )
}
