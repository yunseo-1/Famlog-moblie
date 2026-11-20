package com.famlog.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.famlog.R
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.designsystem.theme.categoryOf
import com.famlog.core.model.CandidateType

/** "채팅에서 등록" 태그 (Figma 03 Event Row: category-bg + category 색 Caption, radius full). 후보 승인으로 생긴 항목만 */
@Composable
fun ChatSourceTag(type: CandidateType, modifier: Modifier = Modifier) {
    val (accent, background) = FamTheme.colors.categoryOf(type)
    Text(
        stringResource(R.string.source_tag),
        style = FamTheme.type.caption,
        color = accent,
        modifier = modifier
            .background(background, CircleShape)
            .padding(horizontal = FamDimens.sm, vertical = 2.dp),
    )
}

/** 상세 화면 한 줄: [채팅에서 등록] 원문 보기 (ARC-06) */
@Composable
fun ChatSourceRow(type: CandidateType, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FamDimens.sm),
    ) {
        ChatSourceTag(type)
        Text(
            stringResource(R.string.source_open),
            style = FamTheme.type.label,
            color = FamTheme.colors.brandPrimary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .heightIn(min = FamDimens.minTouchTarget)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = FamDimens.xs, vertical = FamDimens.md),
        )
    }
}
