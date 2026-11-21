package com.famlog.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.data.AgePolicy
import com.famlog.core.data.StartDestination
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonSize
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.SocialProvider

@Composable
fun LoginScreen(
    onNavigate: (StartDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.destinations.collect(onNavigate) }

    when (state.step) {
        AuthStep.LOGIN -> LoginContent(
            loadingProvider = state.loadingProvider,
            message = state.message,
            onLogin = { provider -> viewModel.login(provider, context) },
            modifier = modifier,
        )
        AuthStep.SIGNUP -> {
            BackHandler(onBack = viewModel::backToLogin)
            SignupContent(
                state = state,
                onBack = viewModel::backToLogin,
                onAgreeAll = viewModel::setAgreeAll,
                onAgreeTerms = viewModel::setAgreeTerms,
                onAgreePrivacy = viewModel::setAgreePrivacy,
                onNicknameChange = viewModel::onNicknameChange,
                onBirthYmChange = viewModel::onBirthYmChange,
                onSignup = viewModel::signup,
                modifier = modifier,
            )
        }
    }
    state.restorePrompt?.let { prompt ->
        RestoreDialog(prompt = prompt, restoring = state.restoring, onRestore = viewModel::restore, onCancel = viewModel::cancelRestore)
    }
}

/** 삭제 예정 계정으로 로그인했다 (ACC-01): 서버 문구(복구 기한) + 복구할지 */
@Composable
fun RestoreDialog(prompt: UiText, restoring: Boolean, onRestore: () -> Unit, onCancel: () -> Unit) {
    val colors = FamTheme.colors
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            Text(
                prompt.asString() + "\n" + stringResource(R.string.restore_body),
                style = FamTheme.type.bodyMedium,
                color = colors.textPrimary,
            )
        },
        confirmButton = {
            TextButton(onClick = onRestore, enabled = !restoring) {
                Text(stringResource(R.string.restore_confirm), color = colors.brandPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !restoring) {
                Text(stringResource(R.string.restore_cancel), color = colors.textSecondary)
            }
        },
        containerColor = colors.bgDefault,
    )
}

/** SCR-01. Figma에 화면이 없어 컴포넌트·토큰으로 구성했다. 소셜 버튼은 각 사의 브랜드 가이드를 따른다 */
@Composable
fun LoginContent(
    loadingProvider: SocialProvider?,
    message: UiText?,
    onLogin: (SocialProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FamDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.app_name),
            style = FamTheme.type.titleLarge,
            color = colors.brandPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(FamDimens.sm))
        Text(
            text = stringResource(R.string.login_tagline),
            style = FamTheme.type.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        if (message != null) {
            Text(
                text = message.asString(),
                style = FamTheme.type.bodyMedium,
                color = colors.statusDanger,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = FamDimens.md),
            )
        }
        SocialLoginButton(
            text = stringResource(R.string.login_kakao),
            icon = painterResource(R.drawable.ic_kakao_symbol),
            iconTint = colors.kakaoLabel,
            containerColor = colors.kakaoContainer,
            contentColor = colors.kakaoLabel,
            border = null,
            loading = loadingProvider == SocialProvider.KAKAO,
            enabled = loadingProvider == null,
            onClick = { onLogin(SocialProvider.KAKAO) },
        )
        Spacer(Modifier.size(FamDimens.sm))
        SocialLoginButton(
            text = stringResource(R.string.login_google),
            icon = painterResource(R.drawable.ic_google_logo),
            iconTint = Color.Unspecified, // 로고 원래 색
            containerColor = colors.googleContainer,
            contentColor = colors.googleLabel,
            border = BorderStroke(1.dp, colors.googleBorder),
            loading = loadingProvider == SocialProvider.GOOGLE,
            enabled = loadingProvider == null,
            onClick = { onLogin(SocialProvider.GOOGLE) },
        )
        Spacer(Modifier.size(FamDimens.xxl))
    }
}

@Composable
private fun SocialLoginButton(
    text: String,
    icon: Painter,
    iconTint: Color,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(FamDimens.radiusMd),
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = contentColor, strokeWidth = 2.dp)
        } else {
            Icon(painter = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(FamDimens.sm))
            Text(text = text, style = FamTheme.type.bodyMediumStrong)
        }
    }
}

/** 가입 단계: 필수 동의 2개 + 닉네임 + 출생연월 (AUTH-02, 사양서 8.3) */
@Composable
fun SignupContent(
    state: AuthUiState,
    onBack: () -> Unit,
    onAgreeAll: (Boolean) -> Unit,
    onAgreeTerms: (Boolean) -> Unit,
    onAgreePrivacy: (Boolean) -> Unit,
    onNicknameChange: (String) -> Unit,
    onBirthYmChange: (String) -> Unit,
    onSignup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    var showTermsNotice by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        FamTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FamDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FamDimens.lg),
        ) {
            Text(
                text = stringResource(R.string.signup_title),
                style = FamTheme.type.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )

            Column {
                AgreementRow(
                    text = stringResource(R.string.signup_agree_all),
                    checked = state.agreeTerms && state.agreePrivacy,
                    onCheckedChange = onAgreeAll,
                    strong = true,
                )
                HorizontalDivider(color = colors.borderDefault)
                AgreementRow(
                    text = stringResource(R.string.signup_agree_terms),
                    checked = state.agreeTerms,
                    onCheckedChange = onAgreeTerms,
                    onView = { showTermsNotice = true },
                )
                AgreementRow(
                    text = stringResource(R.string.signup_agree_privacy),
                    checked = state.agreePrivacy,
                    onCheckedChange = onAgreePrivacy,
                    onView = { showTermsNotice = true },
                )
            }

            FamTextField(
                value = state.nickname,
                onValueChange = onNicknameChange,
                label = stringResource(R.string.signup_nickname_label),
                placeholder = stringResource(R.string.signup_nickname_placeholder),
                supportingText = stringResource(
                    R.string.text_length,
                    state.nickname.length,
                    AuthViewModel.NICKNAME_MAX,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            FamTextField(
                value = state.birthYm,
                onValueChange = onBirthYmChange,
                label = stringResource(R.string.signup_birth_label),
                placeholder = stringResource(R.string.signup_birth_placeholder),
                supportingText = stringResource(R.string.signup_birth_help),
                errorText = when (state.birthCheck) {
                    BirthCheck.INVALID -> stringResource(R.string.signup_birth_invalid)
                    BirthCheck.UNDER_AGE -> stringResource(R.string.signup_under_age, AgePolicy.MINIMUM_AGE)
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )

            if (state.message != null) {
                Text(state.message.asString(), style = FamTheme.type.bodyMedium, color = colors.statusDanger)
            }
        }
        FamButton(
            text = stringResource(R.string.signup_submit),
            onClick = onSignup,
            enabled = state.canSignup,
            loading = state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(FamDimens.screenPadding),
        )
    }

    if (showTermsNotice) {
        AlertDialog(
            onDismissRequest = { showTermsNotice = false },
            confirmButton = {
                FamButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = { showTermsNotice = false },
                    style = FamButtonStyle.GHOST,
                    size = FamButtonSize.SMALL,
                )
            },
            title = { Text(stringResource(R.string.signup_terms_notice_title)) },
            text = { Text(stringResource(R.string.signup_terms_notice_body)) },
            containerColor = colors.bgDefault,
        )
    }
}

@Composable
private fun AgreementRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    strong: Boolean = false,
    onView: (() -> Unit)? = null,
) {
    val colors = FamTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = FamDimens.minTouchTarget)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.brandPrimary,
                    uncheckedColor = colors.borderStrong,
                    checkmarkColor = colors.textOnBrand,
                ),
            )
            Spacer(Modifier.size(FamDimens.sm))
            Text(
                text = text,
                style = if (strong) FamTheme.type.bodyMediumStrong else FamTheme.type.bodyMedium,
                color = colors.textPrimary,
            )
        }
        if (onView != null) {
            FamButton(
                text = stringResource(R.string.signup_view_terms),
                onClick = onView,
                style = FamButtonStyle.GHOST,
                size = FamButtonSize.SMALL,
            )
        }
    }
}
