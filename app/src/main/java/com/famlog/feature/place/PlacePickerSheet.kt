package com.famlog.feature.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.PlaceSummary

/** 장소 고르기 시트 상태 (열려 있을 때만) */
data class PlacePicker(val loading: Boolean = false, val failed: Boolean = false, val places: List<PlaceSummary> = emptyList())

/** 우리 그룹 장소 고르기 (일정 장소 연결·별칭). 이미 골랐으면 맨 위 "장소 빼기"로 연결을 비운다 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerSheet(
    picker: PlacePicker,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onPick: (PlaceSummary?) -> Unit,
) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder) },
    ) {
        Text(
            stringResource(R.string.place_pick_title),
            style = FamTheme.type.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = FamDimens.lg, vertical = FamDimens.sm).semantics { heading() },
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (selectedId != null) {
                item(key = "clear") {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.padding(horizontal = FamDimens.sm)) {
                        Text(stringResource(R.string.place_pick_clear), style = FamTheme.type.label, color = colors.statusDanger)
                    }
                }
            }
            when {
                picker.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(FamDimens.xxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.brandPrimary)
                    }
                }
                picker.failed -> item(key = "failed") {
                    Text(
                        stringResource(R.string.place_load_failed),
                        style = FamTheme.type.bodyMedium,
                        color = colors.statusDanger,
                        modifier = Modifier.padding(FamDimens.lg),
                    )
                }
                picker.places.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(R.string.place_pick_empty),
                        style = FamTheme.type.bodyMedium,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(FamDimens.lg),
                    )
                }
            }
            items(picker.places, key = { it.id }) { place ->
                PlaceRow(
                    name = place.name,
                    subtitle = listOfNotNull(stringResource(place.category.labelRes()), place.address).joinToString(" · "),
                    rating = null,
                    onClick = { onPick(place) },
                    modifier = if (place.id == selectedId) Modifier.background(colors.brandSubtle) else Modifier,
                )
            }
        }
    }
}
