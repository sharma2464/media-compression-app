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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressionPreviewFrames
import com.sharma2464.mediacompression.compress.CompressionPreviewMilestones
import com.sharma2464.mediacompression.compress.CompressionPreviewSynthetic
import com.sharma2464.mediacompression.compress.VideoMetadata
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.debug.DebugSessionLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private fun clampPanOffset(offset: Float, containerPx: Float, scale: Float): Float {
    if (scale <= 1f) return 0f
    val max = containerPx * (scale - 1f) / 2f
    return offset.coerceIn(-max, max)
}

/** Same pixel size so [ContentScale.Crop] aligns at the wipe divider. */
private fun matchPreviewBitmapSize(source: Bitmap, reference: Bitmap?): Bitmap {
    if (reference == null) return source
    if (source.width == reference.width && source.height == reference.height) return source
    return Bitmap.createScaledBitmap(
        source,
        reference.width,
        reference.height,
        true,
    )
}

@Composable
fun CompressionComparePreview(
    sourceUri: String?,
    fileKind: FileKind?,
    livePercent: Int,
    durationMs: Long?,
    encodeOutputPath: String?,
    finishedOutputPath: String?,
    jobSettings: CompressJobSettings? = null,
    videoMeta: VideoMetadata? = null,
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
                val atLiveEdge = frameIndex >= list.lastIndex
                milestones.value = (list + livePercent).distinct().sorted()
                if (atLiveEdge || list.isEmpty()) {
                    frameIndex = milestones.value.lastIndex
                }
            }
        }
    }

    val atLiveEdge = frameIndex >= milestones.value.lastIndex
    val displayPercent = if (atLiveEdge || milestones.value.isEmpty()) {
        livePercent
    } else {
        milestones.value[frameIndex]
    }
    val syntheticStepPercent = (displayPercent / CompressionPreviewMilestones.PERCENT_STEP) *
        CompressionPreviewMilestones.PERCENT_STEP
    val previewFramePercent = if (atLiveEdge) syntheticStepPercent else displayPercent
    val liveEncodeBucket = livePercent / CompressionPreviewMilestones.PERCENT_STEP
    val livePercentRef = remember { mutableIntStateOf(livePercent) }
    livePercentRef.intValue = livePercent
    val canStepBack = frameIndex > 0
    val canStepForward = frameIndex < milestones.value.lastIndex

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var compressedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var compressedIsSynthetic by remember { mutableStateOf(false) }

    val compressedPath = CompressionPreviewFrames.resolveCompressedPreviewPath(
        finishedOutputPath,
        encodeOutputPath,
    )
    val encodingInProgress = compressedPath != null && livePercent < 100
    val compareTimelinePercent = when {
        !encodingInProgress -> previewFramePercent
        !atLiveEdge -> previewFramePercent.coerceAtMost(livePercent)
        else -> (livePercent / CompressionPreviewMilestones.PERCENT_STEP) *
            CompressionPreviewMilestones.PERCENT_STEP
    }

    LaunchedEffect(
        sourceUri,
        fileKind,
        compareTimelinePercent,
        liveEncodeBucket,
        durationMs,
        encodeOutputPath,
        finishedOutputPath,
        jobSettings,
        videoMeta,
    ) {
        if (isPhoto || sourceUri == null) return@LaunchedEffect
        var lastPollKey = -1
        var lastLoggedSeek = -1L
        while (isActive) {
            val path = CompressionPreviewFrames.resolveCompressedPreviewPath(
                finishedOutputPath,
                encodeOutputPath,
            )
            val encoding = path != null && livePercentRef.intValue < 100
            val pollKey = (compareTimelinePercent * 1000) + liveEncodeBucket
            val shouldPoll = path != null && encoding
            if (shouldPoll && pollKey == lastPollKey) {
                delay(800)
                continue
            }
            if (!shouldPoll && pollKey == lastPollKey) {
                return@LaunchedEffect
            }
            val encodePct = livePercentRef.intValue.coerceIn(0, 100)
            val settings = jobSettings
            val meta = videoMeta
            val load = CompressionPreviewFrames.loadCompareFrames(
                context = context,
                sourceUri = sourceUri,
                encodedPath = path,
                sourceDurationMs = durationMs,
                timelinePercent = compareTimelinePercent,
                encodeProgressPercent = encodePct,
                encodingInProgress = encoding,
                jobSettings = settings,
                videoMeta = meta,
                syntheticFromOriginal = { original, vm, js ->
                    CompressionPreviewSynthetic.fromOriginal(original, vm, js)
                },
            )
            val orig = load.original
            val compRaw = load.compressed
            val comp = when {
                orig != null && compRaw != null -> matchPreviewBitmapSize(compRaw, orig)
                else -> compRaw
            }
            if (compRaw != null && comp !== compRaw) compRaw.recycle()
            originalBitmap = orig
            compressedBitmap = comp
            compressedIsSynthetic = load.compressedIsSynthetic
            lastPollKey = pollKey
            val branch = when {
                comp != null && !load.compressedIsSynthetic -> "encoded"
                comp != null && load.compressedIsSynthetic -> "synthetic"
                else -> "none"
            }
            val wipeMode = when {
                orig != null && comp != null -> "wipe"
                orig != null -> "original_only"
                comp != null -> "compressed_only"
                else -> "loading"
            }
            // #region agent log
            if (load.seekTimeUs != lastLoggedSeek) {
                lastLoggedSeek = load.seekTimeUs
                DebugSessionLog.log(
                    context,
                    "P3",
                    "CompressionComparePreview.kt:compareLoad",
                    "frame_pair",
                    mapOf(
                        "branch" to branch,
                        "wipeMode" to wipeMode,
                        "seekTimeUs" to load.seekTimeUs,
                        "compareTimelinePercent" to compareTimelinePercent,
                        "encodePct" to encodePct,
                        "encoding" to encoding,
                        "encodedDurationMs" to load.encodedDurationMs,
                        "origWxH" to "${orig?.width}x${orig?.height}",
                        "compWxH" to "${comp?.width}x${comp?.height}",
                        "livePercent" to livePercentRef.intValue,
                    ),
                    runId = "preview-sync",
                )
            }
            // #endregion
            if (!shouldPoll) return@LaunchedEffect
            if (livePercentRef.intValue >= 100 && branch == "encoded") break
            delay(800)
        }
    }

    val beforeImage = remember(originalBitmap) { originalBitmap?.asImageBitmap() }
    val afterImage = remember(compressedBitmap, compressedIsSynthetic) { compressedBitmap?.asImageBitmap() }

    var wipeFraction by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Column(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("compress_compare_preview"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (isPhoto && sourceUri != null) {
                BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                    val w = constraints.maxWidth.toFloat()
                    val h = constraints.maxHeight.toFloat()
                    ZoomableImageLayer(
                        containerWidthPx = w,
                        containerHeightPx = h,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        onTransform = { newScale, newOx, newOy ->
                            scale = newScale
                            offsetX = newOx
                            offsetY = offsetY
                        },
                    ) {
                        PhotoCompareContent(sourceUri, compressedPath)
                    }
                }
            } else {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    val dividerX = (wipeFraction * widthPx).roundToInt()

                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                                .transformable(
                                    rememberTransformableState { zoomChange, panChange, _ ->
                                        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                        scale = newScale
                                        if (newScale <= 1f) {
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            offsetX = clampPanOffset(offsetX + panChange.x, widthPx, newScale)
                                            offsetY = clampPanOffset(offsetY + panChange.y, heightPx, newScale)
                                        }
                                    },
                                )
                                .pointerInput(canStepBack, canStepForward, widthPx) {
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
                            CompareWipeContent(
                                beforeImage = beforeImage,
                                afterImage = afterImage,
                                wipeFraction = wipeFraction,
                            )
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
                        }
                        LabelChip("Original", Modifier.align(Alignment.TopStart))
                        LabelChip(
                            if (compressedIsSynthetic) "Compressed (est.)" else "Compressed",
                            Modifier.align(Alignment.TopEnd),
                        )
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
private fun CompareWipeContent(
    beforeImage: ImageBitmap?,
    afterImage: ImageBitmap?,
    wipeFraction: Float,
) {
    when {
        beforeImage != null && afterImage != null -> {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val slotW = maxWidth
                val dividerW = slotW * wipeFraction.coerceIn(0.05f, 0.95f)
                Image(
                    bitmap = afterImage,
                    contentDescription = "Compressed",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(dividerW)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                ) {
                    Image(
                        bitmap = beforeImage,
                        contentDescription = "Original",
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(slotW)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        beforeImage != null && afterImage == null -> {
            Image(
                bitmap = beforeImage,
                contentDescription = "Original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        afterImage != null -> {
            Image(
                bitmap = afterImage,
                contentDescription = "Compressed",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        beforeImage != null -> {
            Image(
                bitmap = beforeImage,
                contentDescription = "Original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading preview…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ZoomableImageLayer(
    containerWidthPx: Float,
    containerHeightPx: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        if (newScale <= 1f) {
            onTransform(1f, 0f, 0f)
        } else {
            onTransform(
                newScale,
                clampPanOffset(offsetX + panChange.x, containerWidthPx, newScale),
                clampPanOffset(offsetY + panChange.y, containerHeightPx, newScale),
            )
        }
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
