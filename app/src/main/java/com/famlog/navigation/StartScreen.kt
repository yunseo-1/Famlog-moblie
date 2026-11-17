package com.famlog.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.data.StartDestination
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme

@Composable
fun StartScreen(
    onNavigate: (StartDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StartViewModel = hiltViewModel(),
) {
    val unavailable by viewModel.unavailable.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.destinations.collect(onNavigate) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FamDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(FamDimens.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = FamTheme.type.titleLarge, color = FamTheme.colors.brandPrimary)
        if (unavailable) {
            Text(
                text = stringResource(R.string.error_network),
                style = FamTheme.type.bodyMedium,
                color = FamTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            FamButton(
                text = stringResource(R.string.action_retry),
                onClick = viewModel::resolve,
                style = FamButtonStyle.SECONDARY,
            )
        } else {
            CircularProgressIndicator(color = FamTheme.colors.brandPrimary)
        }
    }
}
