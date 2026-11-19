package com.famlog.feature.place

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.component.FamChip
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.PlaceCard
import com.famlog.core.model.PlaceCategory

/** 장소 아이콘: category/place 배경 원 + 초록 핀 (Figma 장소 색 규칙) */
@Composable
fun PlaceIconBadge(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).background(FamTheme.colors.categoryPlaceBg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tab_place),
            contentDescription = null,
            tint = FamTheme.colors.categoryPlace,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** 별점 표시: 채운 별 하나 + 숫자 ("★ 4") */
@Composable
fun RatingBadge(rating: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.place_rating_value, rating)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = FamTheme.colors.categoryPlace, modifier = Modifier.size(14.dp))
        Text(rating.toString(), style = FamTheme.type.caption, color = FamTheme.colors.textSecondary)
    }
}

/** 별점 고르기 1~5. 고른 별을 다시 누르면 지운다 (ViewModel이 처리) */
@Composable
fun RatingPicker(rating: Int?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        (1..5).forEach { star ->
            val filled = rating != null && star <= rating
            Box(
                modifier = Modifier
                    .size(FamDimens.minTouchTarget)
                    .selectable(selected = rating == star, role = Role.RadioButton, onClick = { onPick(star) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = stringResource(R.string.place_rating_star, star),
                    tint = if (filled) FamTheme.colors.categoryPlace else FamTheme.colors.surfacePlaceholder,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** 카테고리 하나 고르기 (공유·수정 시트) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(selected: PlaceCategory, onSelect: (PlaceCategory) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
        PlaceCategory.entries.forEach { category ->
            FamChip(text = stringResource(category.labelRes()), selected = category == selected, onClick = { onSelect(category) })
        }
    }
}

/**
 * 채팅방 장소 카드 (type=PLACE). 공유한 순간의 이름·카테고리·지역·별점을 보인다.
 * 누르면 장소 상세, 길게 누르면 메시지 메뉴.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaceMessageCard(card: PlaceCard, onClick: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FamTheme.colors
    val shape = RoundedCornerShape(FamDimens.radiusLg)
    Column(
        modifier = modifier
            .width(240.dp)
            .clip(shape)
            .background(colors.bgDefault)
            .border(1.dp, colors.borderDefault, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(FamDimens.md),
        verticalArrangement = Arrangement.spacedBy(FamDimens.sm),
    ) {
        Text(stringResource(R.string.place_card_label), style = FamTheme.type.label, color = colors.categoryPlace)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FamDimens.sm)) {
            PlaceIconBadge(size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(card.name, style = FamTheme.type.bodyMediumStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(stringResource(card.category.labelRes()), regionText(card.region1, card.region2)).joinToString(" · ")
                Text(meta, style = FamTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            card.rating?.let { RatingBadge(it) }
        }
    }
}

/** 목록 한 줄: 아이콘 + 장소명 + "맛집 · 아빠 공유 · 11/12" (+ 별점) */
@Composable
fun PlaceRow(
    name: String,
    subtitle: String,
    rating: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 이미 여백이 있는 곳(상세 화면 필드)에 넣을 때는 0 */
    horizontalPadding: Dp = FamDimens.screenPadding,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = FamDimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        PlaceIconBadge()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = FamTheme.type.bodyMediumStrong, color = FamTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = FamTheme.type.caption, color = FamTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        rating?.let { RatingBadge(it) }
        trailing?.invoke()
    }
}
