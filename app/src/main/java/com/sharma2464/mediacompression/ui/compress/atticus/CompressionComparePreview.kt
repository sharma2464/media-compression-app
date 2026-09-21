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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
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
    val safeLivePercent = livePercent.coerceIn(0, 100)

    LaunchedEffect(safeLivePercent) {
        if (CompressionPreviewMilestones.shouldCaptureMilestone(safeLivePercent, lastCapturedPercent)) {
            lastCapturedPercent = safeLivePercent
            val list = milestones.value
            if (list.isEmpty() || list.last() != safeLivePercent) {
                val atLiveEdge = frameIndex >= list.lastIndex
                milestones.value = (list + safeLivePercent).distinct().sorted()
                if (atLiveEdge || list.isEmpty()) {
                    frameIndex = milestones.value.lastIndex
                }
            }
        }
    }

    val atLiveEdge = frameIndex >= milestones.value.lastIndex
    val displayPercent = if (atLiveEdge || milestones.value.isEmpty()) {
        safeLivePercent
    } else {
        milestones.value[frameIndex]
    }
    val syntheticStepPercent = (displayPercent / CompressionPreviewMilestones.PERCENT_STEP) *
        CompressionPreviewMilestones.PERCENT_STEP
    val previewFramePercent = if (atLiveEdge) syntheticStepPercent else displayPercent
    val liveEncodeBucket = safeLivePercent / CompressionPreviewMilestones.PERCENT_STEP
    val canStepBack = frameIndex > 0
    val canStepForward = frameIndex < milestones.value.lastIndex

    val compressedPath = CompressionPreviewFrames.resolveCompressedPreviewPath(
        finishedOutputPath,
        encodeOutputPath,
    )
    val encodingInProgress = compressedPath != null && safeLivePercent < 100
    val compareTimelinePercent = when {
        !encodingInProgress -> previewFramePercent
        !atLiveEdge -> previewFramePercent.coerceAtMost(safeLivePercent)
        else -> (safeLivePercent / CompressionPreviewMilestones.PERCENT_STEP) *
            CompressionPreviewMilestones.PERCENT_STEP
    }

    var previewLoad by remember { mutableStateOf<CompressionPreviewFrames.CompareFrameLoad?>(null) }
    var framesReady by remember { mutableStateOf(false) }
    var wipeFraction by remember { mutableFloatStateOf(0.5f) }
    val loadEpoch = remember { AtomicInteger(0) }

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
        safeLivePercent,
    ) {
        if (isPhoto || sourceUri == null) return@LaunchedEffect
        val epoch = loadEpoch.incrementAndGet()
        var lastPollKey = -1
        while (isActive) {
            val path = CompressionPreviewFrames.resolveCompressedPreviewPath(
                finishedOutputPath,
                encodeOutputPath,
            )
            val encoding = path != null && safeLivePercent < 100
            val pollKey = (compareTimelinePercent * 1000) + liveEncodeBucket
            val shouldPoll = path != null && encoding
            if (shouldPoll && pollKey == lastPollKey) {
                delay(800)
                continue
            }
            val encodePct = safeLivePercent
            val load = CompressionPreviewFrames.loadCompareFrames(
                context = context,
                sourceUri = sourceUri,
                encodedPath = path,
                sourceDurationMs = durationMs,
                timelinePercent = compareTimelinePercent,
                encodeProgressPercent = encodePct,
                encodingInProgress = encoding,
                jobSettings = jobSettings,
                videoMeta = videoMeta,
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
            if (compRaw != null && comp !== compRaw && !load.compressedIsSynthetic) compRaw.recycle()
            val packed = CompressionPreviewFrames.CompareFrameLoad(
                original = orig,
                compressed = comp,
                compressedIsSynthetic = load.compressedIsSynthetic,
                seekTimeUs = load.seekTimeUs,
                encodedDurationMs = load.encodedDurationMs,
            )
            withContext(Dispatchers.Main) {
                if (!isActive || loadEpoch.get() != epoch) return@withContext
                previewLoad = packed
                framesReady = orig != null
            }
            lastPollKey = pollKey
            val hasEncodedFrame = comp != null && !load.compressedIsSynthetic
            if (!shouldPoll) return@LaunchedEffect
            if (safeLivePercent >= 100 && hasEncodedFrame) break
            delay(800)
        }
    }

    val originalBitmap = previewLoad?.original
    val compressedBitmap = previewLoad?.compressed
    val compressedIsSynthetic = previewLoad?.compressedIsSynthetic == true

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
                            val orig = originalBitmap
                            val comp = compressedBitmap
                            when {
                                orig != null && !compressedIsSynthetic && comp != null -> {
                                    CompareWipeCanvas(
                                        before = orig,
                                        after = comp,
                                        wipeFraction = wipeFraction,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                orig != null -> {
                                    Image(
                                        bitmap = orig.asImageBitmap(),
                                        contentDescription = "Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            if (!compressedIsSynthetic && orig != null && comp != null) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .width(32.dp)
                                        .offset { IntOffset(dividerX - 16, 0) }
                                        .pointerInput(widthPx) {
                                            detectHorizontalDragGestures { change, dragAmount ->
                                                change.consume()
                                                wipeFraction = (wipeFraction + dragAmount / widthPx)
                                                    .coerceIn(0.05f, 0.95f)
                                            }
                                        },
                                )
                            }
                        }
                        if (!framesReady) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Loading preview…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (compressedIsSynthetic) {
                            LabelChip("Preview (est.)", Modifier.align(Alignment.TopCenter))
                        } else {
                            LabelChip("Original", Modifier.align(Alignment.TopStart))
                            LabelChip("Compressed", Modifier.align(Alignment.TopEnd))
                        }
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
