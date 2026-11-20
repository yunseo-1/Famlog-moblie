package com.famlog.feature.place

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonSize
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.CandidateType
import com.famlog.core.model.ItemSource
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceMarkKind
import com.famlog.feature.chat.ChatSourceRow
import com.famlog.feature.chat.SeoulZone
import java.time.LocalDate

/** SCR-32 장소 상세 (PLC-01·05) */
@Composable
fun PlaceDetailScreen(
    onBack: () -> Unit,
    onOpenSource: (groupId: Long, source: ItemSource) -> Unit = { _, _ -> },
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaceDetailEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                PlaceDetailEvent.Closed -> onBack()
            }
        }
    }
    PlaceDetailContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onToggleMark = viewModel::toggleMark,
        onEdit = viewModel::openEdit,
        onDelete = viewModel::delete,
        onCall = { phone -> openOrIgnore(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
        onOpenUrl = { url -> openOrIgnore(context, Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        onOpenSource = onOpenSource,
        snackbarHostState = snackbarHostState,
    )
    state.editSheet?.let { sheet ->
        state.place?.let { place ->
            EditSheet(
                place = place,
                sheet = sheet,
                onDismiss = viewModel::closeEdit,
                onCategoryChange = viewModel::onEditCategory,
                onRatingChange = viewModel::onEditRating,
                onReviewChange = viewModel::onEditReview,
                onSave = viewModel::saveEdit,
            )
        }
    }
}

private fun openOrIgnore(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // 전화·브라우저 앱이 없는 기기 (태블릿 등)
    }
}

@Composable
fun PlaceDetailContent(
    state: PlaceDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleMark: (PlaceMarkKind) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSource: (groupId: Long, source: ItemSource) -> Unit = { _, _ -> },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(colors.bgDefault)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack) {
                if (state.canEdit) {
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.place_edit), style = FamTheme.type.label, color = colors.brandPrimary)
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = !state.deleting) {
                        Text(stringResource(R.string.place_delete), style = FamTheme.type.label, color = colors.statusDanger)
                    }
                }
            }
            val place = state.place
            when {
                place != null -> PlaceBody(place, state, onToggleMark, onCall, onOpenUrl, onOpenSource)
                state.missing -> Missing()
                state.loadFailed -> Column(
                    Modifier.fillMaxSize().padding(FamDimens.screenPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.place_load_failed), style = FamTheme.type.bodyMedium, color = colors.statusDanger)
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brandPrimary)
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.place_delete_title)) },
            text = { Text(stringResource(R.string.place_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.place_delete), color = colors.statusDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.place_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.bgDefault,
        )
    }
}

@Composable
private fun Missing() {
    Column(
        Modifier.fillMaxSize().padding(FamDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FamDimens.sm, Alignment.CenterVertically),
    ) {
        PlaceIconBadge()
        Text(stringResource(R.string.place_missing), style = FamTheme.type.titleMedium, color = FamTheme.colors.textPrimary)
        Text(
            stringResource(R.string.place_missing_body),
            style = FamTheme.type.bodyMedium,
            color = FamTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaceBody(
    place: Place,
    state: PlaceDetailUiState,
    onToggleMark: (PlaceMarkKind) -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSource: (groupId: Long, source: ItemSource) -> Unit,
) {
    val colors = FamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
        verticalArrangement = Arrangement.spacedBy(FamDimens.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.md)) {
            PlaceIconBadge(size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(place.name, style = FamTheme.type.titleLarge, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                val meta = listOfNotNull(stringResource(place.category.labelRes()), place.categoryDetail).joinToString(" · ")
                Text(meta, style = FamTheme.type.bodyMedium, color = colors.textSecondary)
            }
            place.rating?.let { RatingBadge(it) }
        }

        // 채팅에서 등록한 장소: 원문 메시지로 (ARC-06)
        place.source?.let { source ->
            ChatSourceRow(CandidateType.PLACE, onOpen = { onOpenSource(place.groupId, source) })
        }

        place.oneLineReview?.takeIf { it.isNotBlank() }?.let { review ->
            Text(
                "“$review”",
                style = FamTheme.type.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.categoryPlaceBg, RoundedCornerShape(FamDimens.radiusMd))
                    .padding(FamDimens.md),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
            PlaceMarkKind.entries.forEach { kind ->
                val count = place.marks.of(kind).size
                val label = stringResource(kind.labelRes())
                FamChip(
                    text = if (count > 0) "$label $count" else label,
                    selected = state.marked(kind),
                    onClick = { onToggleMark(kind) },
                    role = Role.Checkbox,
                )
            }
        }
        PlaceMarkKind.entries.forEach { kind ->
            val names = state.markedNames(kind)
            if (names.isNotEmpty()) {
                Text(
                    stringResource(R.string.place_mark_names, stringResource(kind.labelRes()), names.joinToString(", ")),
                    style = FamTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
        }

        place.address?.let { address ->
            DetailField(stringResource(R.string.place_field_address)) {
                Text(address, style = FamTheme.type.bodyMedium, color = colors.textPrimary)
            }
        }
        place.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            DetailField(stringResource(R.string.place_field_phone)) {
                Text(phone, style = FamTheme.type.bodyMedium, color = colors.textPrimary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
            place.placeUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
                FamButton(
                    text = stringResource(R.string.place_open_kakao),
                    onClick = { onOpenUrl(url) },
                    style = FamButtonStyle.SECONDARY,
                    size = FamButtonSize.SMALL,
                )
            }
            place.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                FamButton(
                    text = stringResource(R.string.place_call),
                    onClick = { onCall(phone) },
                    style = FamButtonStyle.SECONDARY,
                    size = FamButtonSize.SMALL,
                )
            }
        }

        val sharedLine = listOfNotNull(
            state.nameOf(place.sharedBy)?.let { stringResource(R.string.place_shared_by, it) },
            formatSharedDate(place.createdAt, LocalDate.now(SeoulZone)),
        ).joinToString(" · ")
        Text(sharedLine, style = FamTheme.type.caption, color = colors.textTertiary)
    }
}

private fun PlaceMarkKind.labelRes(): Int = when (this) {
    PlaceMarkKind.VISITED -> R.string.place_mark_visited
    PlaceMarkKind.WANT -> R.string.place_mark_want
}

@Composable
private fun DetailField(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FamDimens.xs)) {
        Text(label, style = FamTheme.type.caption, color = FamTheme.colors.textSecondary)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    place: Place,
    sheet: PlaceEditSheet,
    onDismiss: () -> Unit,
    onCategoryChange: (PlaceCategory) -> Unit,
    onRatingChange: (Int) -> Unit,
    onReviewChange: (String) -> Unit,
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
        PlaceFormFields(
            title = stringResource(R.string.place_edit_title),
            name = place.name,
            address = place.address,
            category = sheet.category,
            rating = sheet.rating,
            review = sheet.review,
            onCategoryChange = onCategoryChange,
            onRatingChange = onRatingChange,
            onReviewChange = onReviewChange,
        ) {
            FamButton(
                text = stringResource(R.string.place_save),
                onClick = onSave,
                loading = sheet.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
