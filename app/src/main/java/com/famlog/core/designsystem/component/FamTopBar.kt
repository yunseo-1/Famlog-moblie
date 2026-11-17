package com.famlog.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.famlog.R
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/** 탭 밖 화면(가입·그룹 시작)의 앱 바: Figma App Bar의 `Show Back = true` 변형. 상태 바 여백은 Scaffold가 준다 */
@Composable
fun FamTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FamDimens.appBarHeight)
            .padding(horizontal = FamDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.action_back),
                tint = FamTheme.colors.textPrimary,
            )
        }
        if (title != null) {
            Text(
                text = title,
                style = FamTheme.type.titleMedium,
                color = FamTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}
