package com.famlog.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.designsystem.theme.toHexRgb
import com.famlog.core.model.FamilyRole

@Composable
fun RoleSetupScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoleSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    RoleSetupContent(
        state = state,
        onRoleSelect = viewModel::selectRole,
        onTitleChange = viewModel::onTitleChange,
        onColorSelect = viewModel::selectColor,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

/** SCR-03 내 가족 역할 설정. Figma에 화면이 없어 Member Chip·Button으로 구성했다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleSetupContent(
    state: RoleSetupUiState,
    onRoleSelect: (FamilyRole) -> Unit,
    onTitleChange: (String) -> Unit,
    onColorSelect: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.brandPrimary)
        }
        return
    }
    if (state.groupName.isEmpty()) {
        // 그룹 정보를 못 읽었다: 다시 시도만 보인다
        Column(
            modifier.fillMaxSize().padding(FamDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FamDimens.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message?.asString().orEmpty(), style = FamTheme.type.bodyMedium, color = colors.textSecondary)
            FamButton(stringResource(R.string.action_retry), onClick = onRetry, style = FamButtonStyle.SECONDARY)
        }
        return
    }
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(FamDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FamDimens.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                Text(
                    text = stringResource(R.string.role_setup_title),
                    style = FamTheme.type.titleLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.role_setup_body, state.groupName),
                    style = FamTheme.type.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            Section(title = stringResource(R.string.role_setup_role_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
                    FamilyRole.entries.forEach { role ->
                        FamChip(
                            text = stringResource(role.labelRes),
                            selected = state.familyRole == role,
                            onClick = { onRoleSelect(role) },
                        )
                    }
                }
            }

            FamTextField(
                value = state.familyTitle,
                onValueChange = onTitleChange,
                label = stringResource(R.string.role_setup_title_label),
                placeholder = stringResource(R.string.role_setup_title_placeholder),
                supportingText = stringResource(
                    R.string.text_length,
                    state.familyTitle.length,
                    RoleSetupViewModel.FAMILY_TITLE_MAX,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Section(title = stringResource(R.string.role_setup_color_label)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FamDimens.xs),
                    verticalArrangement = Arrangement.spacedBy(FamDimens.xs),
                ) {
                    colors.memberPalette.forEachIndexed { index, color ->
                        val hex = color.toHexRgb()
                        val selected = state.calendarColor == hex
                        val description = stringResource(R.string.role_setup_color_item, index + 1)
                        Box(
                            modifier = Modifier
                                .size(FamDimens.minTouchTarget)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onColorSelect(hex) })
                                .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(color, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        tint = colors.textOnBrand,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.message != null) {
                Text(state.message.asString(), style = FamTheme.type.bodyMedium, color = colors.statusDanger)
            }
        }
        FamButton(
            text = stringResource(R.string.role_setup_submit),
            onClick = onSave,
            enabled = state.canSave,
            loading = state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(FamDimens.screenPadding),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        Text(
            text = title,
            style = FamTheme.type.bodyMediumStrong,
            color = FamTheme.colors.textPrimary,
            modifier = Modifier.heightIn(min = 24.dp),
        )
        content()
    }
}

val FamilyRole.labelRes: Int
    get() = when (this) {
        FamilyRole.FATHER -> R.string.family_role_father
        FamilyRole.MOTHER -> R.string.family_role_mother
        FamilyRole.SON -> R.string.family_role_son
        FamilyRole.DAUGHTER -> R.string.family_role_daughter
        FamilyRole.GRANDFATHER -> R.string.family_role_grandfather
        FamilyRole.GRANDMOTHER -> R.string.family_role_grandmother
        FamilyRole.OTHER -> R.string.family_role_other
    }
