package com.famlog.feature.place

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.data.PlaceRepository
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonSize
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.Coordinates
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory

/** SCR-30 장소 탭 [지도] (PLC-04) */
@Composable
fun PlaceMapTab(
    onOpenPlace: (Long) -> Unit,
    viewModel: PlaceMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val controller = remember { KakaoMapController() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationPermission(granted)
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaceMapEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                PlaceMapEvent.RequestLocationPermission -> {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.onLocationPermission(true) else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                is PlaceMapEvent.MoveCamera -> controller.moveTo(event.center)
            }
        }
    }
    PlaceMapContent(
        state = state,
        onSelectAllCategories = viewModel::selectAllCategories,
        onToggleCategory = viewModel::toggleCategory,
        onNearby = viewModel::startNearby,
        onCloseNearby = viewModel::closeNearby,
        onRetry = viewModel::refresh,
        onOpenPlace = onOpenPlace,
        snackbarHostState = snackbarHostState,
        map = { center, modifier ->
            KakaoMapView(
                initialCenter = center,
                pins = state.pins,
                myLocation = state.myLocation,
                controller = controller,
                onViewportChanged = viewModel::onViewportChanged,
                onPinClick = onOpenPlace,
                modifier = modifier,
            )
        },
    )
    state.consentPrompt?.let { prompt ->
        LocationConsentDialog(prompt = prompt, onAgree = viewModel::agreeLocation, onDismiss = viewModel::dismissConsent)
    }
}

/** [map]은 지도 자리 (UI 테스트에서는 빈 상자) */
@Composable
fun PlaceMapContent(
    state: PlaceMapUiState,
    onSelectAllCategories: () -> Unit,
    onToggleCategory: (PlaceCategory) -> Unit,
    onNearby: () -> Unit,
    onCloseNearby: () -> Unit,
    onRetry: () -> Unit,
    onOpenPlace: (Long) -> Unit,
    map: @Composable (center: Coordinates, modifier: Modifier) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    val nearby = state.nearbyVisible
    // 지도 제스처가 목록 스크롤에 먹히지 않게 지도는 스크롤 밖에 둔다
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = FamDimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
            ) {
                item(key = "all") {
                    FamChip(stringResource(R.string.place_filter_all), state.categories.isEmpty(), onSelectAllCategories)
                }
                items(PlaceCategory.entries, key = { it.name }) { category ->
                    FamChip(
                        text = stringResource(category.labelRes()),
                        selected = category in state.categories,
                        onClick = { onToggleCategory(category) },
                        role = Role.Checkbox,
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm)
                    .fillMaxWidth()
                    .height(MAP_HEIGHT)
                    .clip(RoundedCornerShape(FamDimens.radiusMd))
                    .background(colors.surfaceMuted),
            ) {
                val center = state.initialCenter
                if (center != null) map(center, Modifier.fillMaxSize())
                if (state.nearbyAvailable) {
                    FamButton(
                        text = stringResource(R.string.place_nearby),
                        onClick = onNearby,
                        loading = state.nearbyLoading,
                        style = FamButtonStyle.SECONDARY,
                        size = FamButtonSize.SMALL,
                        modifier = Modifier.align(Alignment.BottomStart).padding(FamDimens.sm),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = FamDimens.screenPadding, end = FamDimens.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (nearby != null) {
                        stringResource(R.string.place_nearby_header, PlaceRepository.NEARBY_RADIUS_KM, nearby.size)
                    } else {
                        stringResource(R.string.place_map_header, state.places.size)
                    },
                    style = FamTheme.type.bodyMediumStrong,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).padding(vertical = FamDimens.sm).semantics { heading() },
                )
                if (nearby != null) {
                    TextButton(onClick = onCloseNearby) {
                        Text(stringResource(R.string.place_nearby_close), style = FamTheme.type.label, color = colors.brandPrimary)
                    }
                } else if (state.loading) {
                    CircularProgressIndicator(color = colors.brandPrimary, modifier = Modifier.padding(end = FamDimens.md).size(16.dp))
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = FamDimens.lg)) {
                if (state.loadFailed && nearby == null) {
                    item(key = "failed") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.place_load_failed),
                                style = FamTheme.type.bodyMedium,
                                color = colors.statusDanger,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                            }
                        }
                    }
                }
                if (nearby != null) {
                    if (nearby.isEmpty()) {
                        item(key = "nearby-empty") { Note(stringResource(R.string.place_nearby_empty, PlaceRepository.NEARBY_RADIUS_KM)) }
                    }
                    items(nearby, key = { "n${it.place.id}" }) { item ->
                        PlaceRow(
                            name = item.place.name,
                            subtitle = mapSubtitle(item.place, state),
                            rating = item.place.rating,
                            onClick = { onOpenPlace(item.place.id) },
                            trailing = { Text(formatDistance(item.distanceM), style = FamTheme.type.caption, color = colors.textTertiary) },
                        )
                    }
                } else {
                    if (state.places.isEmpty() && !state.loading && !state.loadFailed) {
                        item(key = "empty") { Note(stringResource(R.string.place_map_empty)) }
                    }
                    items(state.places, key = { it.id }) { place ->
                        PlaceRow(
                            name = place.name,
                            subtitle = mapSubtitle(place, state),
                            rating = place.rating,
                            onClick = { onOpenPlace(place.id) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** "맛집 · 아빠 공유 · 11/12" (목록과 같은 형식) */
@Composable
private fun mapSubtitle(place: Place, state: PlaceMapUiState): String = listOfNotNull(
    stringResource(place.category.labelRes()),
    state.nameOf(place.sharedBy)?.let { stringResource(R.string.place_shared_by, it) },
    formatSharedDate(place.createdAt, state.today),
).joinToString(" · ")

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = FamTheme.type.bodyMedium,
        color = FamTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.xl),
    )
}

/**
 * 위치기반서비스 이용약관 + 개인위치정보 수집·이용 동의 (사양서 8.2, 선택).
 * 약관 전문은 출시 전(S22)에 붙인다. 보여 준 버전([ConsentPrompt.version])으로 동의를 기록한다.
 */
@Composable
fun LocationConsentDialog(prompt: ConsentPrompt, onAgree: () -> Unit, onDismiss: () -> Unit) {
    val colors = FamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_consent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FamDimens.md)) {
                Text(stringResource(R.string.place_consent_body), style = FamTheme.type.bodyMedium, color = colors.textPrimary)
                Text(
                    stringResource(R.string.place_consent_version, prompt.version),
                    style = FamTheme.type.caption,
                    color = colors.textTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree, enabled = !prompt.saving) {
                Text(stringResource(R.string.place_consent_agree), color = colors.brandPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.place_cancel), color = colors.textSecondary) }
        },
        containerColor = colors.bgDefault,
    )
}

private val MAP_HEIGHT = 300.dp
