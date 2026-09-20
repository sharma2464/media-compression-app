package com.sharma2464.mediacompression.ui

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.scan.FolderScanner
import com.sharma2464.mediacompression.scan.findByRelativePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Full-screen before/after comparison for a compressed [entry]: a drag-to-reveal slider
 * (original on the left, compressed on the right) over the two files rendered with the
 * same [TypedPreview] widgets used elsewhere, so PDFs/photos zoom for free without any
 * format-specific code here. Pinch-to-zoom/pan applies to both layers together via one
 * shared graphicsLayer. For video, each layer's own PlayerView transport controls are
 * disabled (they'd overlap and steal the zoom/pan/divider gestures); a single shared
 * transport bar below drives both players in lockstep instead.
 */
private sealed interface BackupLoad {
    data object Loading : BackupLoad
    data object NotFound : BackupLoad
    data class Found(val uri: Uri) : BackupLoad
}

@Composable
fun CompareScreen(entry: FileEntry, rootTreeUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val load by produceState<BackupLoad>(initialValue = BackupLoad.Loading, entry.id) {
        value = withContext(Dispatchers.IO) {
            resolveBackupUri(context, rootTreeUri, entry)?.let { BackupLoad.Found(it) } ?: BackupLoad.NotFound
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            when (val state = load) {
                is BackupLoad.Found -> CompareContent(entry, state.uri, onDismiss)
                BackupLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading original…")
                }
                BackupLoad.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No backup found for this file.")
                }
            }
        }
    }
}

@Composable
private fun CompareContent(entry: FileEntry, originalUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sliderFraction by remember { mutableStateOf(0.5f) }
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var compressedPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var originalPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Clamp pan so the zoomed content can never drift fully off-screen: the max offset in
    // either direction is how far the scaled content overhangs the container. At scale == 1
    // this collapses to zero, so zooming back out always re-centers automatically.
    fun clampPan(offset: Offset, currentScale: Float): Offset {
        val maxX = max(0f, containerSize.width * (currentScale - 1f) / 2f)
        val maxY = max(0f, containerSize.height * (currentScale - 1f) / 2f)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        panOffset = clampPan(panOffset + pan, newScale)
                    }
                },
        ) {
            val zoomPan = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = panOffset.x
                translationY = panOffset.y
            }

            Box(Modifier.fillMaxSize()) {
                // Compressed file: full-size background layer. Video's own transport controls
                // are disabled here — see the shared VideoTransportBar below.
                TypedPreview(
                    Uri.parse(entry.uri), entry.kind, entry.displayName, entry.mimeType,
                    Modifier.fillMaxSize().then(zoomPan),
                    showVideoControls = false,
                    onVideoPlayerReady = { compressedPlayer = it },
                )

                // Original file: drawn full-size, then clipped to the left `sliderFraction`
                // of the width. The clip runs in screen space — outside the pinch-zoom/pan
                // layer — so the reveal boundary always lines up with the divider handle
                // (which is also screen-space), instead of drifting once the user zooms/pans.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * sliderFraction) { this@drawWithContent.drawContent() }
                        },
                ) {
                    TypedPreview(
                        originalUri, entry.kind, entry.displayName, entry.mimeType,
                        Modifier.fillMaxSize().then(zoomPan),
                        showVideoControls = false,
                        onVideoPlayerReady = { originalPlayer = it },
                    )
                }
            }

            // Modern compare control: the divider line itself is the slider, dragged
            // directly instead of a separate Material Slider bar underneath.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * sliderFraction - 16.dp)
                    .fillMaxHeight()
                    .width(32.dp)
                    .pointerInput(maxWidth) {
                        val widthPx = maxWidth.toPx()
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                sliderFraction = (sliderFraction + drag.x / widthPx).coerceIn(0f, 1f)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxHeight().width(2.dp).background(Color.White))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            if (entry.kind == FileKind.VIDEO) {
                val compressed = compressedPlayer
                val original = originalPlayer
                if (compressed != null && original != null) {
                    VideoTransportBar(compressed, original)
                }
            }
            CompareBottomBar(
                beforeSize = Formatter.formatShortFileSize(context, entry.sizeBytes),
                afterSize = Formatter.formatShortFileSize(context, entry.compressedSizeBytes ?: entry.sizeBytes),
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Single play/pause + seek control driving both overlaid video layers in lockstep, since
 * each [androidx.media3.ui.PlayerView]'s own controller is disabled in the compare view
 * (two independent transport UIs would overlap and steal touch from the zoom/pan/divider
 * gestures). Position is polled rather than observed via listener since ExoPlayer has no
 * push-based position API.
 */
@Composable
private fun VideoTransportBar(compressed: ExoPlayer, original: ExoPlayer) {
    var isPlaying by remember { mutableStateOf(compressed.isPlaying) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var scrubRateLabel by remember { mutableStateOf<String?>(null) }

    // Exact (frame-accurate) seeking instead of the default nearest-keyframe seek, so slow
    // scrubbing actually steps frame by frame rather than snapping to sync samples.
    LaunchedEffect(compressed, original) {
        compressed.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        original.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
    }

    DisposableEffect(compressed) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        compressed.addListener(listener)
        onDispose { compressed.removeListener(listener) }
    }

    LaunchedEffect(compressed) {
        while (true) {
            if (!isSeeking) {
                positionMs = compressed.currentPosition
                durationMs = compressed.duration.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))) {
        scrubRateLabel?.let {
            Text(
                it,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
        }
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                val playWhenReady = !isPlaying
                compressed.playWhenReady = playWhenReady
                original.playWhenReady = playWhenReady
            }) {
                Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontWeight = FontWeight.Bold)
            }
            ScrubTrack(
                fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onScrubStart = { isSeeking = true },
                onScrub = { fraction, rateLabel ->
                    positionMs = (fraction * durationMs).toLong()
                    scrubRateLabel = rateLabel
                    compressed.seekTo(positionMs)
                    original.seekTo(positionMs)
                },
                onScrubEnd = {
                    isSeeking = false
                    scrubRateLabel = null
                },
                modifier = Modifier.weight(1f).height(44.dp),
            )
        }
    }
}

/**
 * iOS-style scrub track: dragging straight across maps the full width to the full video
 * duration, but dragging your finger *down* away from the track slows the scrub rate down
 * in stages (down to ~1/10x) so the last bit of vertical travel gives frame-accurate
 * control instead of only ever being able to jump in coarse, one-width-pixel-per-frame steps.
 */
private fun scrubRateFor(dyDp: Float): Pair<Float, String?> = when {
    dyDp < 40f -> 1f to null
    dyDp < 90f -> 0.5f to "1/2×"
    dyDp < 140f -> 0.25f to "1/4×"
    else -> 0.1f to "Fine"
}

@Composable
private fun ScrubTrack(
    fraction: Float,
    onScrubStart: () -> Unit,
    onScrub: (fraction: Float, rateLabel: String?) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val widthPx = with(density) { maxWidth.toPx() }
        var dragFraction by remember { mutableStateOf(fraction) }
        var startY by remember { mutableStateOf(0f) }

        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Color.White),
        )
        Box(
            Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (fraction.coerceIn(0f, 1f) * widthPx - with(density) { 8.dp.toPx() }).toInt(),
                        0,
                    )
                }
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragStart = { start ->
                            dragFraction = fraction
                            startY = start.y
                            onScrubStart()
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val dyDp = with(density) { kotlin.math.abs(change.position.y - startY).toDp().value }
                            val (rate, label) = scrubRateFor(dyDp)
                            dragFraction = (dragFraction + (drag.x * rate) / widthPx).coerceIn(0f, 1f)
                            onScrub(dragFraction, label)
                        },
                        onDragEnd = { onScrubEnd() },
                    )
                },
        )
    }
}

@Composable
private fun CompareBottomBar(beforeSize: String, afterSize: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CornerLabel(label = "Before", size = beforeSize, alignEnd = false)
        Surface(
            onClick = onDismiss,
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Text(
                "✕  Close",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        CornerLabel(label = "After", size = afterSize, alignEnd = true)
    }
}

@Composable
private fun CornerLabel(label: String, size: String, alignEnd: Boolean) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(size, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun resolveBackupUri(context: Context, rootTreeUri: Uri, entry: FileEntry): Uri? {
    val path = entry.backupRelativePath ?: return null
    val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return null
    // REPLACE_IN_PLACE mode moved the original under ORIGINALS/; COMPRESSED_COPY mode never
    // touched it, so it's still sitting at its original path in the source root.
    root.findFile(FolderScanner.ORIGINALS_DIR_NAME)?.let { findByRelativePath(it, path)?.uri }?.let { return it }
    return findByRelativePath(root, path)?.uri
}
