package com.famlog.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famlog.R
import com.famlog.core.designsystem.asString
import com.famlog.core.designsystem.component.FamButton
import com.famlog.core.designsystem.component.FamButtonStyle
import com.famlog.core.designsystem.component.FamTopBar
import com.famlog.core.designsystem.component.GroupAvatar
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.Candidate
import com.famlog.core.model.CandidateEntity
import com.famlog.core.model.CandidateStatus
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.DismissReason
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.ReactionEmoji
import com.famlog.feature.group.shareInvite
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** SCR-11 그룹 채팅방 (Figma 02 목표 구조: App Bar "우리 가족 (4)" + Back + 서랍, bg/subtle, 하단 Chat Input) */
@Composable
fun ChatRoomScreen(
    onBack: () -> Unit,
    onOpenMedia: (groupId: Long, messageId: Long, index: Int) -> Unit,
    onOpenPlace: (placeId: Long) -> Unit = {},
    onOpenSchedule: (scheduleId: Long) -> Unit = {},
    viewModel: ChatRoomViewModel = hiltViewModel(),
    candidateViewModel: CandidateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val candidates by candidateViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var scrollKey by remember { mutableStateOf<String?>(null) }
    var resumedBefore by remember { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.setVisible(true)
        // 다시 돌아왔으면 그동안 놓친 후보 처리 결과를 받는다 (처음은 CandidateViewModel이 받는다)
        if (resumedBefore) candidateViewModel.refresh()
        resumedBefore = true
        onPauseOrDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatRoomEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                is ChatRoomEvent.ShareInvite -> context.shareInvite(event.groupName, event.code)
                is ChatRoomEvent.ScrollTo -> scrollKey = event.key
            }
        }
    }
    LaunchedEffect(candidateViewModel) {
        candidateViewModel.events.collect { event ->
            when (event) {
                is CandidateEvent.Message -> snackbarHostState.showSnackbar(event.text.asString(context))
                is CandidateEvent.ShowEvidence -> viewModel.focus(event.seq, event.spans)
                is CandidateEvent.OpenEntity -> when (event.type) {
                    CandidateEntity.SCHEDULE -> onOpenSchedule(event.id)
                    CandidateEntity.PLACE -> onOpenPlace(event.id)
                    CandidateEntity.TODO -> Unit
                }
            }
        }
    }

    // Photo Picker: 권한 없이 고른 파일만 받는다 (사양서 8.6). 한 번에 30개
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris -> viewModel.onMediaPicked(uris.map { it.toString() }) }

    var menuTarget by remember { mutableStateOf<ChatMessage?>(null) }
    ChatRoomContent(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onCancelReply = viewModel::cancelReply,
        onLoadOlder = viewModel::loadOlder,
        onLongPress = { menuTarget = it },
        onReactionClick = { message, emoji -> viewModel.toggleReaction(message, emoji) },
        onInvite = viewModel::invite,
        snackbarHostState = snackbarHostState,
        onAttach = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        },
        onOpenMedia = { message, index -> message.id?.let { onOpenMedia(message.groupId, it, index) } },
        onOpenPlace = onOpenPlace,
        candidates = candidates,
        candidateActions = CandidateCardActions(
            onApprove = candidateViewModel::approve,
            onEdit = candidateViewModel::edit,
            onDismiss = candidateViewModel::dismiss,
            onUndo = candidateViewModel::undo,
            onOpen = candidateViewModel::openEntity,
            onShowEvidence = candidateViewModel::showEvidence,
        ),
        scrollKey = scrollKey,
        onScrolled = { scrollKey = null },
    )

    candidates.sheet?.let { sheet ->
        CandidateSheetDialog(
            sheet = sheet,
            members = candidates.members,
            today = candidates.today,
            actions = CandidateSheetActions(
                onDismiss = candidateViewModel::closeSheet,
                onSubmit = candidateViewModel::submitSheet,
                onShowEvidence = { evidence ->
                    candidateViewModel.closeSheet()
                    candidateViewModel.showEvidence(evidence)
                },
                onTitleChange = candidateViewModel::onTitleChange,
                onDateChange = candidateViewModel::onDateChange,
                onClearDueDate = candidateViewModel::clearDueDate,
                onAllDayChange = candidateViewModel::onAllDayChange,
                onStartTimeChange = candidateViewModel::onStartTimeChange,
                onEndTimeChange = candidateViewModel::onEndTimeChange,
                onToggleParticipant = candidateViewModel::toggleParticipant,
                onLocationChange = candidateViewModel::onLocationChange,
                onAssigneeChange = candidateViewModel::onAssigneeChange,
                onPlaceSelect = candidateViewModel::onPlaceSelect,
                onCategoryChange = candidateViewModel::onCategoryChange,
                onReviewChange = candidateViewModel::onReviewChange,
                onQueryChange = candidateViewModel::onQueryChange,
                onSearch = candidateViewModel::searchPlace,
            ),
        )
    }

    menuTarget?.let { target ->
        MessageMenuSheet(
            actions = viewModel.actionsFor(target),
            myReaction = target.reactions.firstOrNull { state.myUserId in it.userIds }?.emoji,
            onDismiss = { menuTarget = null },
            onReact = { viewModel.toggleReaction(target, it) },
            onReply = { viewModel.startReply(target) },
            onDelete = { viewModel.delete(target) },
            onReport = viewModel::report,
            onRetry = { viewModel.retry(target) },
            onDiscard = { viewModel.discard(target) },
        )
    }
}

@Composable
fun ChatRoomContent(
    state: ChatRoomUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
    onLoadOlder: () -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    onReactionClick: (ChatMessage, ReactionEmoji) -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAttach: (() -> Unit)? = null,
    onOpenMedia: (ChatMessage, Int) -> Unit = { _, _ -> },
    onOpenPlace: (Long) -> Unit = {},
    candidates: CandidateUiState = CandidateUiState(),
    candidateActions: CandidateCardActions = CandidateCardActions(),
    scrollKey: String? = null,
    onScrolled: () -> Unit = {},
) {
    val colors = FamTheme.colors
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 서랍은 오른쪽에서 연다: 바깥만 오른쪽→왼쪽 배치로 두고 안쪽은 원래 방향으로 되돌린다
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ChatDrawer(state = state, onInvite = onInvite)
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(colors.bgSubtle)
                            .imePadding(),
                    ) {
                        FamTopBar(
                            onBack = onBack,
                            title = stringResource(R.string.chat_room_title, state.groupName, state.memberCount),
                            modifier = Modifier.background(colors.bgDefault),
                            actions = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_menu),
                                        contentDescription = stringResource(R.string.chat_room_drawer_open),
                                        tint = colors.textPrimary,
                                    )
                                }
                            },
                        )
                        MessageList(
                            state = state,
                            onLoadOlder = onLoadOlder,
                            onLongPress = onLongPress,
                            onReactionClick = onReactionClick,
                            onOpenMedia = onOpenMedia,
                            onOpenPlace = onOpenPlace,
                            candidates = candidates,
                            candidateActions = candidateActions,
                            scrollKey = scrollKey,
                            onScrolled = onScrolled,
                            modifier = Modifier.weight(1f),
                        )
                        state.replyTo?.let { reply ->
                            ReplyBar(
                                name = state.nameOf(reply.senderId) ?: stringResource(R.string.chat_unknown_member),
                                body = reply.body.orEmpty(),
                                onCancel = onCancelReply,
                            )
                        }
                        ChatInputBar(
                            value = state.input,
                            onValueChange = onInputChange,
                            canSend = state.canSend,
                            onSend = onSend,
                            onAttach = onAttach,
                        )
                    }
                    SnackbarHost(
                        snackbarHostState,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp),
                    )
                }
            }
        }
    }
}

/** 최신 메시지가 아래. 위 끝 근처까지 올리면 이전 메시지를 더 받는다 */
@Composable
private fun MessageList(
    state: ChatRoomUiState,
    onLoadOlder: () -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    onReactionClick: (ChatMessage, ReactionEmoji) -> Unit,
    onOpenMedia: (ChatMessage, Int) -> Unit,
    onOpenPlace: (Long) -> Unit,
    candidates: CandidateUiState,
    candidateActions: CandidateCardActions,
    scrollKey: String?,
    onScrolled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val rows = remember(state.rows) { state.rows.asReversed() }

    // 원문 보기: 근거 메시지가 화면 가운데쯤 오게 옮긴다
    LaunchedEffect(scrollKey) {
        val key = scrollKey ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == key }
        if (index >= 0) listState.animateScrollToItem(index, scrollOffset = -listState.layoutInfo.viewportSize.height / 3)
        onScrolled()
    }

    val nearTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_OLDER_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearTop }.distinctUntilChanged().filter { it }.collect { onLoadOlder() }
    }

    // 새 메시지: 직전 최신 메시지가 보이고 있었거나 내가 보냈으면 맨 아래로.
    // 여러 개가 한꺼번에 붙으면(알림을 눌러 들어온 직후 등) 목록이 이전 줄을 붙잡아 위치 번호로는 판단할 수 없다
    val newest = rows.firstOrNull() as? ChatRow.Message
    var shownNewestKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(newest?.key) {
        val message = newest?.message ?: return@LaunchedEffect
        val previous = shownNewestKey
        shownNewestKey = newest.key
        val sawPrevious = previous == null || listState.layoutInfo.visibleItemsInfo.any { it.key == previous }
        if (sawPrevious || message.senderId == state.myUserId) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = FamDimens.screenPadding, vertical = FamDimens.sm),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                is ChatRow.Date -> DateSeparator(formatDateSeparator(row.date, state.today))
                is ChatRow.Message -> MessageRow(
                    row, state, onLongPress, onReactionClick, onOpenMedia, onOpenPlace, candidates, candidateActions,
                )
            }
        }
        if (state.loadingOlder) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(FamDimens.sm), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = FamTheme.colors.brandPrimary, strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    row: ChatRow.Message,
    state: ChatRoomUiState,
    onLongPress: (ChatMessage) -> Unit,
    onReactionClick: (ChatMessage, ReactionEmoji) -> Unit,
    onOpenMedia: (ChatMessage, Int) -> Unit,
    onOpenPlace: (Long) -> Unit,
    candidates: CandidateUiState,
    candidateActions: CandidateCardActions,
) {
    val message = row.message
    when {
        message.isSystem -> SystemMessage(message.body.orEmpty())
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChatBubble(
                message = message,
                mine = message.senderId == state.myUserId,
                senderName = state.nameOf(message.senderId),
                replyName = message.replyTo?.let { state.nameOf(it.senderId) },
                showSender = row.showSender,
                showTime = row.showTime,
                myUserId = state.myUserId,
                onLongPress = { onLongPress(message) },
                onReactionClick = { onReactionClick(message, it.emoji) },
                mediaBaseUrl = state.mediaBaseUrl,
                onOpenMedia = { onOpenMedia(message, it) },
                onOpenPlace = onOpenPlace,
                highlights = message.id?.let { state.highlights[it] }.orEmpty(),
            )
            // 이 메시지가 마지막 근거인 후보: 대기 중이면 카드, 승인됐으면 Resolved Chip (ARC-05)
            message.id?.let { candidates.byAnchor[it] }?.forEach { candidate ->
                CandidateItem(candidate, message, candidates, candidateActions)
            }
        }
    }
}

/** 카드·칩 동작 */
data class CandidateCardActions(
    val onApprove: (Candidate) -> Unit = {},
    val onEdit: (Candidate) -> Unit = {},
    val onDismiss: (Candidate, DismissReason?) -> Unit = { _, _ -> },
    val onUndo: (Candidate) -> Unit = {},
    val onOpen: (Candidate) -> Unit = {},
    val onShowEvidence: (Candidate) -> Unit = {},
)

@Composable
private fun CandidateItem(candidate: Candidate, anchor: ChatMessage, candidates: CandidateUiState, actions: CandidateCardActions) {
    val busy = candidate.id in candidates.busy
    if (candidate.status == CandidateStatus.APPROVED) {
        ResolvedChip(
            candidate = candidate,
            deciderName = candidates.nameOf(candidate.decidedBy),
            canUndo = candidate.id in candidates.undoable,
            busy = busy,
            onOpen = { actions.onOpen(candidate) },
            onUndo = { actions.onUndo(candidate) },
        )
    } else {
        CandidateCard(
            candidate = candidate,
            anchor = anchor,
            members = candidates.members,
            busy = busy,
            onApprove = { actions.onApprove(candidate) },
            onEdit = { actions.onEdit(candidate) },
            onDismiss = { actions.onDismiss(candidate, it) },
            onShowEvidence = { actions.onShowEvidence(candidate) },
        )
    }
}

/** 길게 누르기 메뉴: 공감 6종 · 답장 · 삭제(내 메시지 5분 이내) · 신고 / 실패한 메시지는 다시 보내기·지우기 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageMenuSheet(
    actions: MessageActions,
    myReaction: ReactionEmoji?,
    onDismiss: () -> Unit,
    onReact: (ReactionEmoji) -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = FamTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.bgDefault,
        dragHandle = {
            BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = colors.surfacePlaceholder)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = FamDimens.lg)
                .navigationBarsPadding(),
        ) {
            if (actions.canReact) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FamDimens.lg, vertical = FamDimens.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ReactionEmoji.entries.forEach { emoji ->
                        val selected = emoji == myReaction
                        Box(
                            modifier = Modifier
                                .size(FamDimens.minTouchTarget)
                                .background(if (selected) colors.brandSubtle else colors.bgDefault, CircleShape)
                                .clickable(role = Role.Button) {
                                    onReact(emoji)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji.glyph, style = FamTheme.type.titleMedium)
                        }
                    }
                }
                HorizontalDivider(color = colors.borderDefault)
            }
            if (actions.canReply) MenuItem(stringResource(R.string.chat_menu_reply)) { onReply(); onDismiss() }
            if (actions.canRetry) MenuItem(stringResource(R.string.chat_menu_retry)) { onRetry(); onDismiss() }
            if (actions.canDiscard) MenuItem(stringResource(R.string.chat_menu_discard)) { onDiscard(); onDismiss() }
            if (actions.canDelete) {
                MenuItem(stringResource(R.string.chat_menu_delete), danger = true) { onDelete(); onDismiss() }
            }
            if (actions.canReport) MenuItem(stringResource(R.string.chat_menu_report)) { onReport(); onDismiss() }
        }
    }
}

@Composable
private fun MenuItem(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text,
        style = FamTheme.type.bodyLarge,
        color = if (danger) FamTheme.colors.statusDanger else FamTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = FamDimens.minTouchTarget)
            .padding(horizontal = FamDimens.xl, vertical = FamDimens.md),
    )
}

/**
 * SCR-12 채팅방 서랍: 구성원, 가족 초대.
 * 사진 모아보기(S10-2 이후)·링크 모아보기(CHAT-07)·공지(CHAT-08)·그룹 설정(S20)은 아직 없다.
 */
@Composable
private fun ChatDrawer(state: ChatRoomUiState, onInvite: () -> Unit) {
    val colors = FamTheme.colors
    ModalDrawerSheet(drawerContainerColor = colors.bgDefault, drawerShape = RoundedCornerShape(0.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(FamDimens.lg),
            verticalArrangement = Arrangement.spacedBy(FamDimens.sm),
        ) {
            Text(
                text = stringResource(R.string.chat_drawer_title),
                style = FamTheme.type.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.chat_drawer_members, state.members.size),
                style = FamTheme.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = FamDimens.sm),
            )
            state.members.values
                .sortedWith(compareByDescending<Member> { it.userId == state.myUserId }.thenByDescending { it.role })
                .forEach { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FamDimens.md),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        GroupAvatar(name = member.displayName, size = 36.dp)
                        Text(
                            text = member.displayName,
                            style = FamTheme.type.bodyMedium,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (member.userId == state.myUserId) Tag(stringResource(R.string.chat_me))
                        when (member.role) {
                            GroupRole.OWNER -> Tag(stringResource(R.string.chat_role_owner))
                            GroupRole.ADMIN -> Tag(stringResource(R.string.chat_role_admin))
                            GroupRole.MEMBER -> Unit
                        }
                    }
                }
            if (state.myRole.canInvite) {
                FamButton(
                    text = stringResource(R.string.chat_drawer_invite),
                    onClick = onInvite,
                    style = FamButtonStyle.SECONDARY,
                    loading = state.inviting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = FamDimens.md),
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        style = FamTheme.type.caption,
        color = FamTheme.colors.textSecondary,
        modifier = Modifier
            .background(FamTheme.colors.surfaceMuted, CircleShape)
            .padding(horizontal = FamDimens.sm, vertical = 2.dp),
    )
}

private const val LOAD_OLDER_THRESHOLD = 5
private const val MAX_PICK = 30
