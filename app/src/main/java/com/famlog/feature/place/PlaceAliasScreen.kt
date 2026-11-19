package com.famlog.feature.place

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamTextField
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.PlaceAlias

/** 별칭 장소 (PLC-06). 장소 탭 리스트 위 "우리 가족 별칭"에서 들어온다 */
@Composable
fun PlaceAliasScreen(
    onBack: () -> Unit,
    onOpenPlace: (Long) -> Unit,
    viewModel: PlaceAliasViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaceAliasEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
            }
        }
    }
    PlaceAliasContent(
        state = state,
        onBack = onBack,
        onAdd = viewModel::openSheet,
        onDelete = viewModel::delete,
        onOpenPlace = onOpenPlace,
        onRetry = viewModel::retry,
        snackbarHostState = snackbarHostState,
    )
    state.sheet?.let { sheet ->
        AliasSheetDialog(
            sheet = sheet,
            onDismiss = viewModel::closeSheet,
            onAliasChange = viewModel::onAliasChange,
            onAddressChange = viewModel::onAddressChange,
            onPickPlace = viewModel::openPlacePicker,
            onSave = viewModel::save,
        )
    }
    state.placePicker?.let { picker ->
        PlacePickerSheet(
            picker = picker,
            selectedId = state.sheet?.place?.id,
            onDismiss = viewModel::closePlacePicker,
            onPick = viewModel::onPlaceChange,
        )
    }
}

@Composable
fun PlaceAliasContent(
    state: PlaceAliasUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (PlaceAlias) -> Unit,
    onOpenPlace: (Long) -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack, title = stringResource(R.string.place_alias_title))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = FamDimens.fabSize + FamDimens.xxxl),
            ) {
                item(key = "guide") {
                    Text(
                        stringResource(R.string.place_alias_guide),
                        style = FamTheme.type.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
                    )
                }
                when {
                    state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(FamDimens.xxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brandPrimary)
                        }
                    }
                    state.loadFailed -> item(key = "failed") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.place_alias_load_failed),
                                style = FamTheme.type.bodyMedium,
                                color = colors.statusDanger,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                            }
                        }
                    }
                    state.aliases.isEmpty() -> item(key = "empty") {
                        Text(
                            stringResource(R.string.place_alias_empty),
                            style = FamTheme.type.bodyMedium,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = FamDimens.xxxl),
                        )
                    }
                }
                items(state.aliases, key = { it.id }) { alias ->
                    AliasRow(
                        alias = alias,
                        canDelete = state.canDelete(alias),
                        onDelete = { onDelete(alias) },
                        onOpenPlace = onOpenPlace,
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = colors.brandPrimary,
            contentColor = colors.textOnBrand,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(FamDimens.lg).navigationBarsPadding(),
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.place_alias_add))
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** 별칭 + (연결한 장소 이름·주소 또는 주소 글). 연결한 장소가 있으면 눌러서 상세로 */
@Composable
private fun AliasRow(alias: PlaceAlias, canDelete: Boolean, onDelete: () -> Unit, onOpenPlace: (Long) -> Unit) {
    val colors = FamTheme.colors
    val place = alias.place
    val subtitle = listOfNotNull(place?.name, place?.address ?: alias.addressText).joinToString(" · ")
        .ifEmpty { stringResource(R.string.place_alias_no_place) }
    PlaceRow(
        name = alias.alias,
        subtitle = subtitle,
        rating = null,
        onClick = { place?.let { onOpenPlace(it.id) } },
        trailing = {
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.place_alias_delete, alias.alias),
                        tint = colors.textTertiary,
                    )
                }
            }
        },
    )
}

/** 별칭 추가: 별칭(필수 30자) + 연결할 우리 장소(선택) + 주소 글(선택 200자) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AliasSheetDialog(
    sheet: AliasSheet,
    onDismiss: () -> Unit,
    onAliasChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPickPlace: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = FamDimens.lg, end = FamDimens.lg, bottom = FamDimens.xl)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(FamDimens.md),
        ) {
            Text(
                stringResource(R.string.place_alias_add),
                style = FamTheme.type.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            FamTextField(
                value = sheet.alias,
                onValueChange = onAliasChange,
                label = stringResource(R.string.place_alias_field),
                placeholder = stringResource(R.string.place_alias_hint),
                errorText = when {
                    sheet.duplicated -> stringResource(R.string.place_alias_duplicated)
                    sheet.showErrors && sheet.aliasMissing -> stringResource(R.string.place_alias_required)
                    else -> null
                },
            )
            Text(stringResource(R.string.place_alias_place), style = FamTheme.type.caption, color = colors.textSecondary)
            Surface(
                onClick = onPickPlace,
                shape = RoundedCornerShape(FamDimens.radiusMd),
                border = BorderStroke(1.dp, colors.borderStrong),
                color = colors.bgDefault,
                modifier = Modifier.fillMaxWidth().heightIn(min = FamDimens.minTouchTarget),
            ) {
                Box(Modifier.padding(horizontal = FamDimens.md, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        sheet.place?.name ?: stringResource(R.string.place_none),
                        style = FamTheme.type.bodyLarge,
                        color = colors.textPrimary,
                    )
                }
            }
            FamTextField(
                value = sheet.addressText,
                onValueChange = onAddressChange,
                label = stringResource(R.string.place_alias_address),
                placeholder = stringResource(R.string.place_alias_address_hint),
            )
            FamButton(
                text = stringResource(R.string.place_save),
                onClick = onSave,
                loading = sheet.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
