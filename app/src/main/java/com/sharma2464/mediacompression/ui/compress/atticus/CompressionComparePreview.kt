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

    LaunchedEffect(sourceUri, fileKind, previewFramePercent, durationMs) {
        if (isPhoto || sourceUri == null) return@LaunchedEffect
        originalBitmap = CompressionPreviewFrames.loadVideoFrame(
            context,
            sourceUri,
            previewFramePercent,
            durationMs,
        )
    }

    var lastSyntheticStep by remember { mutableIntStateOf(-1) }

    LaunchedEffect(
        originalBitmap,
        syntheticStepPercent,
        jobSettings,
        videoMeta,
        encodeOutputPath,
        finishedOutputPath,
    ) {
        if (isPhoto) return@LaunchedEffect
        val path = CompressionPreviewFrames.resolveCompressedPreviewPath(
            finishedOutputPath,
            encodeOutputPath,
        )
        val original = originalBitmap
        if (path != null || original == null || jobSettings == null || videoMeta == null) {
            return@LaunchedEffect
        }
        if (syntheticStepPercent == lastSyntheticStep && compressedIsSynthetic && compressedBitmap != null) {
            return@LaunchedEffect
        }
        val synthetic = CompressionPreviewSynthetic.fromOriginal(original, videoMeta, jobSettings)
        compressedBitmap = matchPreviewBitmapSize(synthetic, original)
        if (synthetic !== compressedBitmap) synthetic.recycle()
        compressedIsSynthetic = true
        lastSyntheticStep = syntheticStepPercent
        // #region agent log
        DebugSessionLog.log(
            context,
            "P1",
            "CompressionComparePreview.kt:synthetic",
            "synthetic_once",
            mapOf(
                "syntheticStepPercent" to syntheticStepPercent,
                "origWxH" to "${original.width}x${original.height}",
                "compWxH" to "${compressedBitmap?.width}x${compressedBitmap?.height}",
            ),
            runId = "preview-stable",
        )
        // #endregion
    }

    LaunchedEffect(
        sourceUri,
        previewFramePercent,
        durationMs,
        liveEncodeBucket,
        encodeOutputPath,
        finishedOutputPath,
    ) {
        if (isPhoto || sourceUri == null) return@LaunchedEffect
        var lastEncodedPollKey = -1
        var lastLoggedBranch = ""
        while (isActive) {
            val path = CompressionPreviewFrames.resolveCompressedPreviewPath(
                finishedOutputPath,
                encodeOutputPath,
            )
            if (path == null) {
                delay(800)
                continue
            }
            val pollKey = (previewFramePercent * 1000) + liveEncodeBucket
            if (pollKey == lastEncodedPollKey) {
                delay(800)
                continue
            }
            val encodePct = livePercentRef.intValue.coerceIn(0, 100)
            val encoded = CompressionPreviewFrames.loadEncodedCompareFrame(
                path,
                durationMs,
                previewFramePercent,
                encodePct,
            )
            if (encoded != null && pollKey != lastEncodedPollKey) {
                val ref = originalBitmap
                val matched = if (ref != null) matchPreviewBitmapSize(encoded, ref) else encoded
                compressedBitmap = matched
                if (matched !== encoded) encoded.recycle()
                compressedIsSynthetic = false
                lastEncodedPollKey = pollKey
            }
            val branch = when {
                encoded != null -> "encoded"
                compressedBitmap != null && compressedIsSynthetic -> "synthetic"
                compressedBitmap != null -> "held_encoded"
                else -> "none"
            }
            val wipeMode = when {
                originalBitmap != null && compressedBitmap != null -> "wipe"
                originalBitmap != null -> "original_only"
                compressedBitmap != null -> "compressed_only"
                else -> "loading"
            }
            // #region agent log
            if (branch != lastLoggedBranch) {
                lastLoggedBranch = branch
                DebugSessionLog.log(
                    context,
                    "P2",
                    "CompressionComparePreview.kt:encodedPoll",
                    "branch_change",
                    mapOf(
                        "branch" to branch,
                        "wipeMode" to wipeMode,
                        "pollKey" to pollKey,
                        "compWxH" to "${compressedBitmap?.width}x${compressedBitmap?.height}",
                        "livePercent" to livePercent,
                    ),
                    runId = "preview-stable",
                )
            }
            // #endregion
            if (livePercentRef.intValue >= 100 && encoded != null) break
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
