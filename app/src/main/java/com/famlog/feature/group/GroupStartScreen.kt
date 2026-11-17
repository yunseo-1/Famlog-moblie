package com.famlog.feature.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.data.StartDestination
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import kotlinx.serialization.Serializable

@Serializable
enum class GroupStartMode { CHOOSE, CREATE, JOIN }

/**
 * SCR-02 그룹 시작. 그룹이 없을 때 첫 화면이고, 그룹 전환 시트(SCR-70)의 [새 그룹]·[초대 코드 참여]로도 온다.
 * [onBack]이 null이면 돌아갈 화면이 없는 첫 화면이다.
 */
@Composable
fun GroupStartScreen(
    initialMode: GroupStartMode,
    onNavigate: (StartDestination) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: GroupStartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    LaunchedEffect(viewModel) { viewModel.destinations.collect(onNavigate) }

    // 처음부터 만들기·참여로 들어왔으면 뒤로가기는 이전 화면으로, 고르기에서 들어갔으면 고르기로
    val backToChoose = mode != GroupStartMode.CHOOSE && initialMode == GroupStartMode.CHOOSE
    BackHandler(enabled = backToChoose) {
        viewModel.clearMessage()
        mode = GroupStartMode.CHOOSE
    }
    val topBarBack: (() -> Unit)? = if (backToChoose) {
        {
            viewModel.clearMessage()
            mode = GroupStartMode.CHOOSE
        }
    } else {
        onBack
    }

    GroupStartContent(
        mode = mode,
        state = state,
        onBack = topBarBack,
        onModeChange = { mode = it },
        onNameChange = viewModel::onNameChange,
        onCodeChange = viewModel::onCodeChange,
        onCreate = viewModel::create,
        onJoin = viewModel::join,
        modifier = modifier,
    )
}

@Composable
fun GroupStartContent(
    mode: GroupStartMode,
    state: GroupStartUiState,
    onBack: (() -> Unit)?,
    onModeChange: (GroupStartMode) -> Unit,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        if (onBack != null) FamTopBar(onBack = onBack) else Spacer(Modifier.heightIn(min = FamDimens.appBarHeight))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FamDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FamDimens.lg),
        ) {
            val (title, body) = when (mode) {
                GroupStartMode.CHOOSE -> R.string.group_start_title to R.string.group_start_body
                GroupStartMode.CREATE -> R.string.group_create_title to R.string.group_create_body
                GroupStartMode.JOIN -> R.string.group_join_title to R.string.group_join_body
            }
            Text(
                text = stringResource(title),
                style = FamTheme.type.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(body), style = FamTheme.type.bodyMedium, color = colors.textSecondary)

            when (mode) {
                GroupStartMode.CHOOSE -> Unit
                GroupStartMode.CREATE -> FamTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.group_create_name_label),
                    placeholder = stringResource(R.string.group_create_name_placeholder),
                    supportingText = stringResource(
                        R.string.text_length,
                        state.name.length,
                        GroupStartViewModel.GROUP_NAME_MAX,
                    ),
                    errorText = state.message?.asString(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCreate() }),
                )
                GroupStartMode.JOIN -> FamTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    label = stringResource(R.string.group_join_code_label),
                    placeholder = stringResource(R.string.group_join_code_placeholder),
                    errorText = state.message?.asString(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onJoin() }),
                    visualTransformation = InviteCodeTransformation,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FamDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FamDimens.sm),
        ) {
            when (mode) {
                GroupStartMode.CHOOSE -> {
                    FamButton(
                        text = stringResource(R.string.group_create_action),
                        onClick = { onModeChange(GroupStartMode.CREATE) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FamButton(
                        text = stringResource(R.string.group_join_action),
                        onClick = { onModeChange(GroupStartMode.JOIN) },
                        style = FamButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GroupStartMode.CREATE -> FamButton(
                    text = stringResource(R.string.group_create_submit),
                    onClick = onCreate,
                    enabled = state.canCreate,
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                GroupStartMode.JOIN -> FamButton(
                    text = stringResource(R.string.group_join_submit),
                    onClick = onJoin,
                    enabled = state.canJoin,
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 입력값은 8자리 그대로 두고 화면에만 "K7MP-Q2XA"처럼 가운데 하이픈을 보인다 */
private object InviteCodeTransformation : VisualTransformation {
    private const val SPLIT = INVITE_CODE_LENGTH / 2

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= SPLIT) return TransformedText(text, OffsetMapping.Identity)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset > SPLIT) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (offset > SPLIT) offset - 1 else offset
        }
        return TransformedText(AnnotatedString(raw.take(SPLIT) + "-" + raw.drop(SPLIT)), mapping)
    }
}
