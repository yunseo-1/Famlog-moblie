package com.famlog.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * SCR-14 제안함. 채팅방 앱바 sparkle에서 들어온다.
 * [onOpenOriginal]은 제안함을 닫고 원래 채팅방에서 그 후보의 근거로 가게 한다.
 */
@Composable
fun CandidateInboxScreen(
    onBack: () -> Unit,
    onOpenOriginal: (candidateId: Long) -> Unit,
    viewModel: CandidateInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CandidateEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                // 제안함의 원문 보기는 채팅방으로 넘기고, 대기 후보만 있어 [보기]는 오지 않는다
                is CandidateEvent.ShowEvidence, is CandidateEvent.OpenEntity -> Unit
            }
        }
    }
    val actions = viewModel.actions
    val cardActions = remember(actions) { actions.cardActions().copy(onShowEvidence = { onOpenOriginal(it.id) }) }
    CandidateInboxContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        cardActions = cardActions,
        snackbarHostState = snackbarHostState,
    )
    state.sheet?.let { sheet ->
        CandidateSheetDialog(
            sheet = sheet,
            members = state.members,
            today = state.today,
            actions = remember(actions) {
                actions.sheetActions().copy(
                    onShowEvidence = {
                        val id = actions.sheetState.value?.candidate?.id
                        actions.closeSheet()
                        id?.let(onOpenOriginal)
                    },
                )
            },
        )
    }
}

@Composable
fun CandidateInboxContent(
    state: CandidateInboxUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    cardActions: CandidateCardActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FamTheme.colors
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
    Box(Modifier.fillMaxSize().background(colors.bgSubtle)) {
        Column(Modifier.fillMaxSize()) {
            FamTopBar(
                onBack = onBack,
                title = stringResource(R.string.candidate_inbox_title),
                modifier = Modifier.background(colors.bgDefault),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = FamDimens.screenPadding, end = FamDimens.screenPadding, bottom = FamDimens.xl),
                verticalArrangement = Arrangement.spacedBy(FamDimens.md),
            ) {
                item(key = "guide") {
                    Text(
                        stringResource(R.string.candidate_inbox_guide),
                        style = FamTheme.type.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = FamDimens.md),
                    )
                }
                when {
                    state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(FamDimens.xxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brandPrimary)
                        }
                    }
                    state.loadFailed && state.items.isEmpty() -> item(key = "failed") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.candidate_inbox_failed),
                                style = FamTheme.type.bodyMedium,
                                color = colors.statusDanger,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.place_retry), style = FamTheme.type.label, color = colors.brandPrimary)
                            }
                        }
                    }
                    state.items.isEmpty() -> item(key = "empty") {
                        Text(
                            stringResource(R.string.candidate_inbox_empty),
                            style = FamTheme.type.bodyMedium,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = FamDimens.xxxl),
                        )
                    }
                }
                items(state.items, key = { it.id }) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        anchor = candidate.anchorMessageId?.let { state.anchors[it] },
                        members = state.members,
                        busy = candidate.id in state.busy,
                        onApprove = { cardActions.onApprove(candidate) },
                        onEdit = { cardActions.onEdit(candidate) },
                        onDismiss = { cardActions.onDismiss(candidate, it) },
                        onShowEvidence = { cardActions.onShowEvidence(candidate) },
                        indent = false,
                    )
                }
                if (state.loadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(FamDimens.sm), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = colors.brandPrimary, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

private const val LOAD_MORE_THRESHOLD = 3
