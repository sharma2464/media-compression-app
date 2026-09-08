package com.sharma2464.tindercompression.ui

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
import androidx.media3.ui.PlayerView

@Composable
fun VideoPreview(uri: Uri, modifier: Modifier = Modifier.fillMaxWidth().height(320.dp)) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }
    // PlayerView ships its own play/pause/seek transport controls by default — no extra
    // wiring needed for the "play and pause while comparing" requirement.
    AndroidView(
        modifier = modifier,
        factory = { PlayerView(it).apply { this.player = player } },
    )
}
