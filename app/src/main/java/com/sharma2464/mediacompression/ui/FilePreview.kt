package com.sharma2464.mediacompression.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind

private val DEFAULT_PREVIEW_MODIFIER = Modifier.fillMaxWidth().height(320.dp)

@Composable
fun FilePreview(entry: FileEntry, modifier: Modifier = DEFAULT_PREVIEW_MODIFIER) {
    TypedPreview(Uri.parse(entry.uri), entry.kind, entry.displayName, entry.mimeType, modifier)
}

@Composable
fun TypedPreview(
    uri: Uri,
    kind: FileKind,
    displayName: String,
    mimeType: String,
    modifier: Modifier = DEFAULT_PREVIEW_MODIFIER,
    showVideoControls: Boolean = true,
    onVideoPlayerReady: (ExoPlayer) -> Unit = {},
) {
    when (kind) {
        FileKind.PHOTO, FileKind.LIVE_PHOTO -> AsyncImage(
            model = uri,
            contentDescription = displayName,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        FileKind.VIDEO -> VideoPreview(uri, modifier, showControls = showVideoControls, onPlayerReady = onVideoPlayerReady)
        else -> Text("Preview not available for $mimeType", modifier = modifier)
    }
}
