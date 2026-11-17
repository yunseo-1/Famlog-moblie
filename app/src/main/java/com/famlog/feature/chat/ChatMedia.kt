package com.famlog.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.famlog.R
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.ChatMessage
import com.famlog.core.model.MediaKind
import com.famlog.core.model.MediaStatus
import com.famlog.core.model.SendState
import com.famlog.core.network.MediaUrls
import com.famlog.core.network.MediaVariant

/** 묶음 한 칸에 보일 것: 올리는 중인 내 파일 또는 서버 미디어 */
private sealed interface Tile {
    val kind: MediaKind
    val durationSec: Int?

    data class Local(val uri: String, override val kind: MediaKind, override val durationSec: Int?) : Tile

    data class Remote(
        val id: Long,
        override val kind: MediaKind,
        val status: MediaStatus,
        override val durationSec: Int?,
        val aspect: Float?,
    ) : Tile
}

private fun ChatMessage.tiles(): List<Tile> =
    if (media.isNotEmpty()) {
        media.map {
            val aspect = if (it.width != null && it.height != null && it.height > 0) it.width.toFloat() / it.height else null
            Tile.Remote(it.id, it.kind, it.status, it.durationSec, aspect)
        }
    } else {
        localMedia.map { Tile.Local(it.uri, it.kind, it.durationSec) }
    }

/**
 * 사진 묶음·동영상 (CHAT-05, 18.1 "한 묶음으로 보이고 썸네일이 먼저"). 너비 [GRID_WIDTH].
 * 1개는 크게(비율 유지), 여러 개는 2~3열 격자, 9개를 넘으면 마지막 칸에 +N.
 * 서버 처리 중(UPLOADED)이면 "처리 중", 올리는 중이면 위에 "올리는 중 n/N".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    message: ChatMessage,
    baseUrl: String,
    onOpen: (index: Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = message.tiles()
    if (tiles.isEmpty()) return
    val uploading = message.sendState != SendState.SENT && message.localMedia.isNotEmpty()
    Box(modifier.clip(RoundedCornerShape(FamDimens.radiusMd))) {
        if (tiles.size == 1) {
            val tile = tiles.single()
            val aspect = ((tile as? Tile.Remote)?.aspect ?: 1f).coerceIn(0.6f, 1.6f)
            TileView(
                tile = tile,
                index = 0,
                total = 1,
                baseUrl = baseUrl,
                variant = MediaVariant.THUMB_M,
                modifier = Modifier
                    .width(GRID_WIDTH)
                    .aspectRatio(aspect),
                onOpen = { if (!uploading) onOpen(0) },
                onLongPress = onLongPress,
            )
        } else {
            val columns = if (tiles.size == 2 || tiles.size == 4) 2 else 3
            val shown = tiles.take(MAX_TILES)
            val cell: Dp = (GRID_WIDTH - GAP * (columns - 1)) / columns
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                shown.chunked(columns).forEachIndexed { row, rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        rowTiles.forEachIndexed { column, tile ->
                            val index = row * columns + column
                            val hidden = if (index == MAX_TILES - 1) tiles.size - MAX_TILES else 0
                            Box {
                                TileView(
                                    tile = tile,
                                    index = index,
                                    total = tiles.size,
                                    baseUrl = baseUrl,
                                    variant = MediaVariant.THUMB_S,
                                    modifier = Modifier.size(cell),
                                    onOpen = { if (!uploading) onOpen(index) },
                                    onLongPress = onLongPress,
                                )
                                if (hidden > 0) {
                                    Box(
                                        Modifier
                                            .size(cell)
                                            .background(FamTheme.colors.mediaOverlay),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.chat_media_more, hidden),
                                            style = FamTheme.type.titleMedium,
                                            color = FamTheme.colors.onMediaViewer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (uploading) {
            val done = message.localMedia.count { it.uploaded }
            Box(
                Modifier
                    .matchParentSize()
                    .background(FamTheme.colors.mediaOverlay)
                    .combinedClickable(onClick = onLongPress, onLongClick = onLongPress),
                contentAlignment = Alignment.Center,
            ) {
                if (message.sendState == SendState.FAILED) {
                    Icon(
                        painter = painterResource(R.drawable.ic_alert),
                        contentDescription = stringResource(R.string.chat_failed),
                        tint = FamTheme.colors.onMediaViewer,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.chat_media_uploading, done, message.localMedia.size),
                        style = FamTheme.type.label,
                        color = FamTheme.colors.onMediaViewer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(
    tile: Tile,
    index: Int,
    total: Int,
    baseUrl: String,
    variant: MediaVariant,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FamTheme.colors
    val description = if (tile.kind == MediaKind.VIDEO) {
        stringResource(R.string.chat_media_video_description, formatDuration(tile.durationSec))
    } else {
        stringResource(R.string.chat_media_photo_description, index + 1, total)
    }
    Box(
        modifier = modifier
            .background(colors.surfacePlaceholder)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (tile) {
            is Tile.Local -> if (tile.kind == MediaKind.IMAGE) {
                AsyncImage(
                    model = tile.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is Tile.Remote -> when (tile.status) {
                MediaStatus.READY -> AsyncImage(
                    model = MediaUrls.contentUrl(baseUrl, tile.id, variant),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                MediaStatus.UPLOADED, MediaStatus.PENDING_UPLOAD -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FamDimens.xs),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.textTertiary, strokeWidth = 2.dp)
                    if (total == 1) {
                        Text(stringResource(R.string.chat_media_processing), style = FamTheme.type.caption, color = colors.textTertiary)
                    }
                }
                else -> Icon(
                    painter = painterResource(R.drawable.ic_alert),
                    contentDescription = stringResource(R.string.chat_media_failed),
                    tint = colors.textTertiary,
                )
            }
        }
        // 실패·거부·처리 중인 동영상에는 재생 표시를 겹치지 않는다
        val playable = tile is Tile.Local || (tile as Tile.Remote).status == MediaStatus.READY
        if (tile.kind == MediaKind.VIDEO && playable) VideoBadge(tile.durationSec)
    }
}

@Composable
private fun VideoBadge(durationSec: Int?) {
    Box(Modifier.fillMaxSize()) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = null,
            tint = FamTheme.colors.onMediaViewer,
            modifier = Modifier
                .align(Alignment.Center)
                .background(FamTheme.colors.mediaOverlay, CircleShape)
                .padding(FamDimens.sm)
                .size(20.dp),
        )
        if (durationSec != null) {
            Text(
                text = formatDuration(durationSec),
                style = FamTheme.type.caption,
                color = FamTheme.colors.onMediaViewer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(FamDimens.xs)
                    .background(FamTheme.colors.mediaOverlay, RoundedCornerShape(FamDimens.xs))
                    .padding(horizontal = FamDimens.xs),
            )
        }
    }
}

/** 1:05 */
fun formatDuration(seconds: Int?): String {
    val total = seconds ?: 0
    return "%d:%02d".format(total / 60, total % 60)
}

private val GRID_WIDTH = 240.dp
private val GAP = 2.dp
private const val MAX_TILES = 9
