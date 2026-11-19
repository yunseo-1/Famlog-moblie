package com.famlog.feature.place

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.RegionCounts
import com.famlog.feature.calendar.memberColor

/** SCR-31 장소 탭 (PLC-02·03). 지도는 S15-2에서 [지도|리스트]로 붙인다 */
@Composable
fun PlaceScreen(
    onOpenPlace: (Long) -> Unit,
    onAddPlace: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    PlaceListContent(
        state = state,
        onSelectRegion = viewModel::selectRegion,
        onSelectAllMembers = viewModel::selectAllMembers,
        onToggleMember = viewModel::toggleMember,
        onSelectAllCategories = viewModel::selectAllCategories,
        onToggleCategory = viewModel::toggleCategory,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::refresh,
        onOpenPlace = onOpenPlace,
        onAddPlace = onAddPlace,
        modifier = modifier,
    )
}

@Composable
fun PlaceListContent(
    state: PlaceListUiState,
    onSelectRegion: (String?, String?) -> Unit,
    onSelectAllMembers: () -> Unit,
    onToggleMember: (Long) -> Unit,
    onSelectAllCategories: () -> Unit,
    onToggleCategory: (PlaceCategory) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenPlace: (Long) -> Unit,
    onAddPlace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    var regionSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor) { if (nearEnd && state.nextCursor != null) onLoadMore() }

    val allRegions = stringResource(R.string.place_region_all)
    val regionLabel = regionText(state.filter.region1, state.filter.region2) ?: allRegions

    Box(modifier.fillMaxSize().background(colors.bgDefault)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = FamDimens.fabSize + FamDimens.xxxl),
        ) {
            item(key = "region") {
                RegionButton(
                    label = regionLabel,
                    selected = state.filter.region1 != null,
                    onClick = { regionSheet = true },
                    modifier = Modifier.padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.xs),
                )
            }
            item(key = "members") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = FamDimens.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
                ) {
                    item(key = "all") {
                        FamChip(stringResource(R.string.place_filter_all), state.filter.sharedBy.isEmpty(), onSelectAllMembers)
                    }
                    items(state.members, key = { it.userId }) { member ->
                        FamChip(
                            text = member.displayName,
                            selected = member.userId in state.filter.sharedBy,
                            onClick = { onToggleMember(member.userId) },
                            dotColor = memberColor(member, state.members),
                            role = Role.Checkbox,
                        )
                    }
                }
            }
            item(key = "categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = FamDimens.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
                ) {
                    item(key = "all") {
                        FamChip(stringResource(R.string.place_filter_all), state.filter.categories.isEmpty(), onSelectAllCategories)
                    }
                    items(PlaceCategory.entries, key = { it.name }) { category ->
                        FamChip(
                            text = stringResource(category.labelRes()),
                            selected = category in state.filter.categories,
                            onClick = { onToggleCategory(category) },
                            role = Role.Checkbox,
                        )
                    }
                }
            }
            if (state.loadFailed) {
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
            item(key = "header") {
                val count = state.regions?.countOf(state.filter)
                Text(
                    text = if (count != null) stringResource(R.string.place_list_header, regionLabel, count.toInt()) else regionLabel,
                    style = FamTheme.type.bodyMediumStrong,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .padding(start = FamDimens.screenPadding, end = FamDimens.screenPadding, top = FamDimens.lg, bottom = FamDimens.xs)
                        .semantics { heading() },
                )
            }
            if (state.places.isEmpty() && !state.loading && !state.loadFailed) {
                item(key = "empty") {
                    val filtered = state.filter != PlaceFilter()
                    Text(
                        stringResource(if (filtered) R.string.place_empty_filtered else R.string.place_empty),
                        style = FamTheme.type.bodyMedium,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = FamDimens.screenPadding, vertical = FamDimens.xxxl),
                    )
                }
            }
            items(state.places, key = { it.id }) { place ->
                PlaceRow(
                    name = place.name,
                    subtitle = placeSubtitle(place, state),
                    rating = place.rating,
                    onClick = { onOpenPlace(place.id) },
                )
            }
            if (state.loading && state.places.isEmpty() || state.loadingMore) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(FamDimens.lg), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.brandPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddPlace,
            containerColor = colors.brandPrimary,
            contentColor = colors.textOnBrand,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(FamDimens.lg),
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.place_add))
        }
    }

    if (regionSheet) {
        RegionSheet(
            regions = state.regions,
            filter = state.filter,
            onDismiss = { regionSheet = false },
            onSelect = { region1, region2 ->
                regionSheet = false
                onSelectRegion(region1, region2)
            },
        )
    }
}

/** "맛집 · 아빠 공유 · 11/12" */
@Composable
private fun placeSubtitle(place: Place, state: PlaceListUiState): String {
    val parts = listOfNotNull(
        stringResource(place.category.labelRes()),
        state.nameOf(place.sharedBy)?.let { stringResource(R.string.place_shared_by, it) },
        formatSharedDate(place.createdAt, state.today),
    )
    return parts.joinToString(" · ")
}

/** 지금 필터의 지역에 해당하는 개수. 지역 배지는 구성원·카테고리 필터를 반영한 값이다 */
internal fun RegionCounts.countOf(filter: PlaceFilter): Long? {
    val region1 = filter.region1 ?: return total
    val region = items.firstOrNull { it.region1 == region1 } ?: return 0
    val region2 = filter.region2 ?: return region.count
    return region.children.firstOrNull { it.region2 == region2 }?.count ?: 0
}

/** 지역 선택 버튼: 고른 지역 + ▾ (Member Chip 모양) */
@Composable
private fun RegionButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FamTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = FamDimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.xs),
    ) {
        Text(label, style = FamTheme.type.titleMedium, color = if (selected) colors.brandPrimary else colors.textPrimary)
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = stringResource(R.string.place_region_sheet_title),
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 시·도 → 시·군·구 2단계 선택. 시·도를 누르면 펼치고, 그 안에서 "○○ 전체"나 시·군·구를 고른다 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionSheet(
    regions: RegionCounts?,
    filter: PlaceFilter,
    onDismiss: () -> Unit,
    onSelect: (String?, String?) -> Unit,
) {
    val colors = FamTheme.colors
    var expanded by rememberSaveable { mutableStateOf(filter.region1) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        Text(
            stringResource(R.string.place_region_sheet_title),
            style = FamTheme.type.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = FamDimens.lg, vertical = FamDimens.sm).semantics { heading() },
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item(key = "all") {
                RegionRow(
                    label = stringResource(R.string.place_region_all),
                    count = regions?.total,
                    selected = filter.region1 == null,
                    indent = false,
                    onClick = { onSelect(null, null) },
                )
            }
            regions?.items.orEmpty().forEach { region ->
                item(key = "r1:${region.region1}") {
                    HorizontalDivider(color = colors.borderDefault)
                    RegionRow(
                        label = region.region1,
                        count = region.count,
                        selected = filter.region1 == region.region1 && expanded != region.region1,
                        indent = false,
                        onClick = { expanded = if (expanded == region.region1) null else region.region1 },
                    )
                }
                if (expanded == region.region1) {
                    item(key = "r1all:${region.region1}") {
                        RegionRow(
                            label = stringResource(R.string.place_region_all_of, region.region1),
                            count = region.count,
                            selected = filter.region1 == region.region1 && filter.region2 == null,
                            indent = true,
                            onClick = { onSelect(region.region1, null) },
                        )
                    }
                    // 세종처럼 시·군·구가 없는 곳(region2=null)은 "○○ 전체"로 고른다
                    region.children.filter { it.region2 != null }.forEach { child ->
                        item(key = "r2:${region.region1}:${child.region2}") {
                            RegionRow(
                                label = child.region2.orEmpty(),
                                count = child.count,
                                selected = filter.region1 == region.region1 && filter.region2 == child.region2,
                                indent = true,
                                onClick = { onSelect(region.region1, child.region2) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRow(label: String, count: Long?, selected: Boolean, indent: Boolean, onClick: () -> Unit) {
    val colors = FamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.minTouchTarget)
            .background(if (selected) colors.brandSubtle else colors.bgDefault)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (indent) FamDimens.xxl else FamDimens.lg, end = FamDimens.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (selected) FamTheme.type.bodyMediumStrong else FamTheme.type.bodyMedium,
            color = if (selected) colors.brandPrimary else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                stringResource(R.string.place_count, count.toInt()),
                style = FamTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier
                    .background(colors.surfaceMuted, CircleShape)
                    .padding(horizontal = FamDimens.sm, vertical = 2.dp),
            )
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5
