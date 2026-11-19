package com.famlog.feature.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceSearchItem

/** SCR-32 장소 검색 → 공유 (PLC-01) */
@Composable
fun PlaceSearchScreen(
    onBack: () -> Unit,
    onOpenPlace: (Long) -> Unit,
    viewModel: PlaceSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaceSearchEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                is PlaceSearchEvent.OpenPlace -> onOpenPlace(event.placeId)
            }
        }
    }
    PlaceSearchContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::searchNow,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onSelect = viewModel::select,
        snackbarHostState = snackbarHostState,
    )
    state.sheet?.let { sheet ->
        ShareSheet(
            sheet = sheet,
            onDismiss = viewModel::closeSheet,
            onCategoryChange = viewModel::onCategoryChange,
            onRatingChange = viewModel::onRatingChange,
            onReviewChange = viewModel::onReviewChange,
            onShareToChatChange = viewModel::onShareToChatChange,
            onShare = viewModel::share,
        )
    }
}

@Composable
fun PlaceSearchContent(
    state: PlaceSearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (PlaceSearchItem) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.hasMore) { if (nearEnd && state.hasMore) onLoadMore() }

    Box(Modifier.fillMaxSize().background(colors.bgDefault).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(onBack = onBack, title = stringResource(R.string.place_search_title))
            FamTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.place_search_title),
                placeholder = stringResource(R.string.place_search_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.xs)
                    .focusRequester(focusRequester),
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                when {
                    state.searching -> item(key = "searching") {
                        Box(Modifier.fillMaxWidth().padding(FamDimens.xxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brandPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                    state.searchFailed -> item(key = "failed") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.place_search_failed),
                                style = FamTheme.type.bodyMedium,
                                color = colors.statusDanger,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                            }
                        }
                    }
                    state.noResults -> item(key = "empty") { CenterNote(stringResource(R.string.place_search_empty)) }
                    state.searchedQuery.isEmpty() -> item(key = "guide") { CenterNote(stringResource(R.string.place_search_guide)) }
                }
                if (!state.searching) {
                    items(state.results, key = { it.kakaoPlaceId }) { item ->
                        PlaceRow(
                            name = item.name,
                            subtitle = listOfNotNull(item.categoryDetail, item.address ?: regionText(item.region1, item.region2))
                                .joinToString(" · "),
                            rating = null,
                            onClick = { onSelect(item) },
                            trailing = {
                                when {
                                    item.sharedPlaceId != null -> Text(
                                        stringResource(R.string.place_search_shared),
                                        style = FamTheme.type.caption,
                                        color = colors.categoryPlace,
                                        modifier = Modifier
                                            .background(colors.categoryPlaceBg, CircleShape)
                                            .padding(horizontal = FamDimens.sm, vertical = 2.dp),
                                    )
                                    item.distanceM != null -> Text(
                                        formatDistance(item.distanceM),
                                        style = FamTheme.type.caption,
                                        color = colors.textTertiary,
                                    )
                                }
                            },
                        )
                    }
                }
                if (state.loadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(FamDimens.lg), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brandPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CenterNote(text: String) {
    Text(
        text,
        style = FamTheme.type.bodyMedium,
        color = FamTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.xxxl),
    )
}

/** 공유 시트: 장소 이름·주소 + 분류 + 별점 + 한줄평 + 채팅방에도 알리기 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    sheet: PlaceShareSheet,
    onDismiss: () -> Unit,
    onCategoryChange: (PlaceCategory) -> Unit,
    onRatingChange: (Int) -> Unit,
    onReviewChange: (String) -> Unit,
    onShareToChatChange: (Boolean) -> Unit,
    onShare: () -> Unit,
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
            title = stringResource(R.string.place_share_title),
            name = sheet.item.name,
            address = sheet.item.address,
            category = sheet.category,
            rating = sheet.rating,
            review = sheet.review,
            onCategoryChange = onCategoryChange,
            onRatingChange = onRatingChange,
            onReviewChange = onReviewChange,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.place_share_to_chat),
                    style = FamTheme.type.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = sheet.shareToChat,
                    onCheckedChange = onShareToChatChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.brandPrimary, checkedThumbColor = colors.textOnBrand),
                )
            }
            FamButton(
                text = stringResource(R.string.place_share_action),
                onClick = onShare,
                loading = sheet.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 공유·수정 시트 공통 입력 (분류·별점·한줄평). 아래 버튼은 [footer] */
@Composable
internal fun PlaceFormFields(
    title: String,
    name: String,
    address: String?,
    category: PlaceCategory,
    rating: Int?,
    review: String,
    onCategoryChange: (PlaceCategory) -> Unit,
    onRatingChange: (Int) -> Unit,
    onReviewChange: (String) -> Unit,
    footer: @Composable () -> Unit,
) {
    val colors = FamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = FamDimens.lg, end = FamDimens.lg, bottom = FamDimens.xl)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        Text(title, style = FamTheme.type.titleMedium, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.md)) {
            PlaceIconBadge()
            Column(Modifier.weight(1f)) {
                Text(name, style = FamTheme.type.bodyMediumStrong, color = colors.textPrimary)
                address?.let { Text(it, style = FamTheme.type.caption, color = colors.textSecondary) }
            }
        }
        Text(stringResource(R.string.place_field_category), style = FamTheme.type.caption, color = colors.textSecondary)
        CategoryPicker(selected = category, onSelect = onCategoryChange)
        Text(stringResource(R.string.place_field_rating), style = FamTheme.type.caption, color = colors.textSecondary)
        RatingPicker(rating = rating, onPick = onRatingChange)
        FamTextField(
            value = review,
            onValueChange = onReviewChange,
            label = stringResource(R.string.place_field_review),
            placeholder = stringResource(R.string.place_review_hint),
            supportingText = stringResource(R.string.place_review_count, review.length),
            singleLine = false,
            minLines = 2,
        )
        footer()
    }
}

private const val LOAD_MORE_THRESHOLD = 5
