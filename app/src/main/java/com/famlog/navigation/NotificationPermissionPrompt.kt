package com.famlog.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.famlog.R
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

/**
 * SCR-04 알림 권한 안내 (Android 13+). 탭 화면에 처음 왔을 때 한 번만 보이고,
 * [알림 받기]를 누르면 시스템 권한 창을 띄운다. 이미 허용했거나 12 이하면 보이지 않는다.
 */
@Composable
fun NotificationPermissionPrompt(visible: Boolean, onDone: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    // 시트가 닫힌 뒤에도 결과를 받을 수 있게 시트 밖에서 등록한다
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!visible) return
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
        // 이미 허용했으면 묻지 않은 것으로 끝낸다
        LaunchedEffect(Unit) { onDone() }
        return
    }
    NotificationPermissionSheet(
        onAllow = {
            onDone()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onLater = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPermissionSheet(onAllow: () -> Unit, onLater: () -> Unit) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = {
            BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = FamDimens.lg, end = FamDimens.lg, bottom = FamDimens.xxl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FamDimens.md),
        ) {
            Text(
                text = stringResource(R.string.push_permission_title),
                style = FamTheme.type.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.push_permission_body),
                style = FamTheme.type.bodyMedium,
                color = colors.textSecondary,
            )
            FamButton(
                text = stringResource(R.string.push_permission_allow),
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
            )
            FamButton(
                text = stringResource(R.string.push_permission_later),
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
                style = FamButtonStyle.GHOST,
            )
        }
    }
}
