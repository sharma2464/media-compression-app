package com.sharma2464.mediacompression.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sharma2464.mediacompression.R

@Composable
fun VideoPreview(
    uri: Uri,
    modifier: Modifier = Modifier.fillMaxWidth().height(320.dp),
    showControls: Boolean = true,
    onPlayerReady: (ExoPlayer) -> Unit = {},
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(uri) {
        onPlayerReady(player)
        onDispose { player.release() }
    }
    // PlayerView ships its own play/pause/seek transport controls by default. Callers that
    // drive playback themselves (e.g. a shared before/after transport bar) pass
    // showControls = false and use [onPlayerReady] instead.
    AndroidView(
        modifier = modifier,
        factory = {
            val view = android.view.LayoutInflater.from(it)
                .inflate(R.layout.video_preview_player, null) as PlayerView
            view.apply {
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                useController = showControls
            }
        },
    )
}
