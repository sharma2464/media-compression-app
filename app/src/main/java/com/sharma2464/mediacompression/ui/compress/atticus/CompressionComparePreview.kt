package com.sharma2464.mediacompression.ui.compress.atticus

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharma2464.mediacompression.compress.CompressionPreviewFrames
import com.sharma2464.mediacompression.compress.CompressionPreviewMilestones
import com.sharma2464.mediacompression.data.FileKind
import kotlin.math.roundToInt

@Composable
fun CompressionComparePreview(
    sourceUri: String?,
    fileKind: FileKind?,
    livePercent: Int,
    durationMs: Long?,
    encodeOutputPath: String?,
    finishedOutputPath: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isPhoto = CompressionPreviewFrames.isPhotoKind(fileKind)
    val milestones = remember { mutableStateOf(listOf<Int>()) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var lastCapturedPercent by remember { mutableIntStateOf(-1) }

    LaunchedEffect(livePercent) {
        if (CompressionPreviewMilestones.shouldCaptureMilestone(livePercent, lastCapturedPercent)) {
            lastCapturedPercent = livePercent
            val list = milestones.value
            if (list.isEmpty() || list.last() != livePercent) {
                milestones.value = (list + livePercent).distinct().sorted()
                frameIndex = milestones.value.lastIndex
            }
        }
    }

    val displayPercent = milestones.value.getOrElse(frameIndex) { livePercent }
    val canStepBack = frameIndex > 0
    val canStepForward = frameIndex < milestones.value.lastIndex

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var compressedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastGoodCompressed by remember { mutableStateOf<Bitmap?>(null) }

    val compressedPath = finishedOutputPath?.takeIf { java.io.File(it).exists() }
        ?: encodeOutputPath?.takeIf { java.io.File(it).exists() && java.io.File(it).length() > 1024 }

    LaunchedEffect(sourceUri, fileKind, displayPercent, durationMs, compressedPath) {
        if (isPhoto) {
            originalBitmap = null
            compressedBitmap = null
            return@LaunchedEffect
        }
        if (sourceUri == null) return@LaunchedEffect
        originalBitmap = CompressionPreviewFrames.loadVideoFrame(context, sourceUri, displayPercent, durationMs)
        val encoded = compressedPath?.let {
            CompressionPreviewFrames.loadVideoFrameFromPath(it, displayPercent, durationMs)
        }
        if (encoded != null) {
            compressedBitmap = encoded
            lastGoodCompressed = encoded
        } else {
            compressedBitmap = lastGoodCompressed
        }
    }

    var wipeFraction by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Column(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (isPhoto && sourceUri != null) {
                ZoomableCompareBox {
                    PhotoCompareContent(sourceUri, compressedPath)
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val dividerX = (wipeFraction * widthPx).roundToInt()
                    val dividerDp = with(LocalDensity.current) { dividerX.toDp() }
                    val fullWidth = maxWidth

                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                            .transformable(transformState)
                            .pointerInput(canStepBack, canStepForward) {
                                detectTapGestures { offset ->
                                    if (offset.x < size.width / 3f && canStepBack) {
                                        frameIndex = CompressionPreviewMilestones.stepFrameIndex(
                                            frameIndex,
                                            milestones.value.lastIndex,
                                            -1,
                                        ) ?: frameIndex
                                    } else if (offset.x > size.width * 2f / 3f && canStepForward) {
                                        frameIndex = CompressionPreviewMilestones.stepFrameIndex(
                                            frameIndex,
                                            milestones.value.lastIndex,
                                            1,
                                        ) ?: frameIndex
                                    }
                                }
                            },
                    ) {
                        val after = compressedBitmap
                        val before = originalBitmap
                        if (after != null) {
                            Image(
                                bitmap = after.asImageBitmap(),
                                contentDescription = "Compressed",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (before != null) {
                            Image(
                                bitmap = before.asImageBitmap(),
                                contentDescription = "Original",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Loading preview…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (before != null && after != null) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .width(dividerDp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                            ) {
                                Image(
                                    bitmap = before.asImageBitmap(),
                                    contentDescription = "Original",
                                    modifier = Modifier
                                        .width(fullWidth)
                                        .fillMaxHeight()
                                        .align(Alignment.CenterStart),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        if (before != null && after == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Compressed preview when available",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(24.dp)
                                .offset { IntOffset(dividerX - 12, 0) }
                                .pointerInput(widthPx) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        wipeFraction = (wipeFraction + dragAmount / widthPx)
                                            .coerceIn(0.05f, 0.95f)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .width(3.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        LabelChip("Original", Modifier.align(Alignment.TopStart))
                        LabelChip("Compressed", Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
        Text(
            "Drag divider · pinch to zoom · tap sides for frames",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}

@Composable
private fun LabelChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(8.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ZoomableCompareBox(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offsetX += panChange.x
        offsetY += panChange.y
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            .transformable(transformState),
    ) {
        content()
    }
}

@Composable
private fun PhotoCompareContent(sourceUri: String, compressedPath: String?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = Uri.parse(CompressionPreviewFrames.uriForPreview(sourceUri) ?: sourceUri),
            contentDescription = "Original",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (compressedPath != null) {
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            ) {
                AsyncImage(
                    model = Uri.fromFile(java.io.File(compressedPath)),
                    contentDescription = "Compressed",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
