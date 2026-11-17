package com.famlog.feature.chat

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.famlog.R
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.ChatMedia
import com.famlog.core.model.MediaKind
import com.famlog.core.model.MediaStatus

/** 사진·동영상 전체 화면 (CHAT-05). 좌우로 넘기고, 사진은 두 손가락·두 번 탭으로 확대 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    onClose: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val colors = FamTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.mediaViewerBackground),
    ) {
        val items = media
        if (items == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.onMediaViewer)
        } else if (items.isNotEmpty()) {
            val pager = rememberPagerState(initialPage = viewModel.initialIndex.coerceIn(0, items.lastIndex)) { items.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items[it].id }) { page ->
                MediaPage(
                    media = items[page],
                    imageUrl = viewModel::imageUrl,
                    videoUrl = viewModel::videoUrl,
                    visible = pager.currentPage == page,
                )
            }
            Text(
                text = stringResource(R.string.media_viewer_counter, pager.currentPage + 1, items.size),
                style = FamTheme.type.bodyMediumStrong,
                color = colors.onMediaViewer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = FamDimens.lg),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(FamDimens.xs),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.media_viewer_close),
                    tint = colors.onMediaViewer,
                )
            }
        }
    }
}

@Composable
private fun MediaPage(
    media: ChatMedia,
    imageUrl: (Long) -> String,
    videoUrl: suspend (Long) -> String?,
    visible: Boolean,
) {
    if (media.status != MediaStatus.READY) {
        Message(stringResource(R.string.media_viewer_not_ready))
        return
    }
    when (media.kind) {
        MediaKind.IMAGE -> ZoomableImage(imageUrl(media.id))
        MediaKind.VIDEO -> if (visible) VideoPage(media.id, videoUrl) else Unit
    }
}

@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                    offset = Offset.Zero
                })
            }
            // 확대하지 않았을 때는 좌우 넘기기를 막지 않는다
            .transformable(transform, lockRotationOnZoomPan = true, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** 안드로이드 기본 VideoView + MediaController (재생·일시정지·탐색) */
@Composable
private fun VideoPage(mediaId: Long, videoUrl: suspend (Long) -> String?) {
    var url by remember(mediaId) { mutableStateOf<String?>(null) }
    var failed by remember(mediaId) { mutableStateOf(false) }
    LaunchedEffect(mediaId) {
        url = videoUrl(mediaId)
        failed = url == null
    }
    when {
        failed -> Message(stringResource(R.string.media_viewer_video_error))
        url == null -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = FamTheme.colors.onMediaViewer)
        }
        else -> AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VideoView(context).apply {
                    val controller = MediaController(context)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setOnPreparedListener { start() }
                    setOnErrorListener { _, _, _ ->
                        failed = true
                        true
                    }
                    setVideoURI(Uri.parse(url))
                }
            },
            onRelease = { it.stopPlayback() },
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = FamTheme.type.bodyMedium,
            color = FamTheme.colors.onMediaViewer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(FamDimens.xxl),
        )
    }
}

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
