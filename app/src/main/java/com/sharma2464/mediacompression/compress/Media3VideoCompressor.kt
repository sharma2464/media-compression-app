package com.sharma2464.mediacompression.compress

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Size
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.common.util.Clock
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException
@UnstableApi
internal class Media3VideoCompressor(private val context: Context) {

    suspend fun compress(
        input: File,
        profile: CompressionProfile,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult {
        if (profile.mode == CompressionMode.LOSSLESS_ONLY) {
            val output = File(workDir, input.name)
            input.copyTo(output, overwrite = true)
            onProgress(100)
            return CompressionResult(output, wasLossless = true)
        }

        val output = File(workDir, "${input.nameWithoutExtension}_compressed.mp4")
        return try {
            transcode(input, output, profile, onProgress)
            CompressionResult(output, wasLossless = false)
        } catch (e: IOException) {
            output.delete()
            val passthrough = File(workDir, input.name)
            input.copyTo(passthrough, overwrite = true)
            onProgress(100)
            CompressionResult(passthrough, wasLossless = true)
        }
    }

    private suspend fun transcode(
        input: File,
        output: File,
        profile: CompressionProfile,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val probe = VideoTrackProbe.probe(input)
            val videoBitrate = profile.targetVideoBitrateBps
                ?: targetBitrate(input, profile.videoBitrateFactor)
            val videoMime = when (profile.videoCodec) {
                VideoCodec.H264 -> MimeTypes.VIDEO_H264
                VideoCodec.H265 -> MimeTypes.VIDEO_H265
            }
            val audioPassthrough = VideoTrackProbe.canPassthroughAudio(
                probe,
                profile.removeAudio,
                profile.volumePercent,
            )
            val encoderFactory = VideoEncoderFactories.createWrappingFactory(
                context = context,
                videoBitrateBps = videoBitrate,
                videoMimeType = videoMime,
                targetFps = profile.targetFps?.toFloat(),
                audioPassthrough = audioPassthrough,
            )
            val decoderFactory = DefaultDecoderFactory.Builder(context)
                .setEnableDecoderFallback(true)
                .build()

            val editedItem = buildEditedMediaItem(input, profile)

            val transformerBuilder = Transformer.Builder(context)
                .setVideoMimeType(videoMime)
                .setMaxDelayBetweenMuxerSamplesMs(30_000)
                .setAssetLoaderFactory(DefaultAssetLoaderFactory(context, decoderFactory, Clock.DEFAULT))
                .setEncoderFactory(encoderFactory)
            if (!audioPassthrough && !profile.removeAudio) {
                transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
            }
            val transformer = transformerBuilder
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                })
                .build()

            transformer.start(editedItem, output.path)

            val pollJob = launch {
                val holder = ProgressHolder()
                val stallTracker = StallTracker(STALL_TIMEOUT_MS)
                var lastKnownPercent = 0
                while (isActive) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        lastKnownPercent = holder.progress
                        onProgress(lastKnownPercent)
                    }
                    val stalled = stallTracker.onProgress(state * 1000 + lastKnownPercent)
                    if (stalled) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException(
                                    "Transformer stalled at $lastKnownPercent% (state=$state, " +
                                        "no change for ${STALL_TIMEOUT_MS / 1000}s)",
                                ),
                            )
                        }
                        transformer.cancel()
                        break
                    }
                    delay(500)
                }
            }
            cont.invokeOnCancellation {
                pollJob.cancel()
                transformer.cancel()
            }
        }
    }

    private fun buildEditedMediaItem(input: File, profile: CompressionProfile): EditedMediaItem {
        val mediaItem = MediaItem.fromUri(input.toURI().toString())
        val builder = EditedMediaItem.Builder(mediaItem)
        if (profile.removeAudio) {
            builder.setRemoveAudio(true)
        }
        profile.targetFps?.let { fps ->
            builder.setFrameRate(fps)
        }
        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        profile.targetFps?.let { fps ->
            videoEffects += FrameDropEffect.createDefaultFrameDropEffect(fps.toFloat())
        }
        val targetW = profile.targetWidth
        val targetH = profile.targetHeight
        if (targetW != null && targetH != null) {
            val size = readVideoSize(input)
            if (size != null && (size.width != targetW || size.height != targetH)) {
                videoEffects += Presentation.createForWidthAndHeight(
                    targetW,
                    targetH,
                    Presentation.LAYOUT_SCALE_TO_FIT,
                )
            }
        } else {
            val cap = profile.maxVideoLongEdge
            if (cap != null) {
                val size = readVideoSize(input)
                if (size != null) {
                    val longEdge = maxOf(size.width, size.height)
                    if (longEdge > cap) {
                        val scale = cap.toFloat() / longEdge
                        val w = (size.width * scale).toInt().coerceAtLeast(2)
                        val h = (size.height * scale).toInt().coerceAtLeast(2)
                        videoEffects += Presentation.createForWidthAndHeight(
                            w,
                            h,
                            Presentation.LAYOUT_SCALE_TO_FIT,
                        )
                    }
                }
            }
        }
        if (videoEffects.isNotEmpty()) {
            builder.setEffects(Effects(emptyList(), videoEffects))
        }
        return builder.build()
    }

    private fun readVideoSize(input: File): Size? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(input.path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w != null && h != null) Size(w, h) else null
        } finally {
            retriever.release()
        }
    }

    private fun targetBitrate(input: File, factor: Double): Int {
        val retriever = MediaMetadataRetriever()
        val sourceBitrate = try {
            retriever.setDataSource(input.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        } finally {
            retriever.release()
        } ?: DEFAULT_SOURCE_BITRATE
        return (sourceBitrate * factor).toInt().coerceAtLeast(MIN_BITRATE)
    }

    private companion object {
        const val MIN_BITRATE = 300_000
        const val DEFAULT_SOURCE_BITRATE = 4_000_000
        const val STALL_TIMEOUT_MS = 60_000L
    }
}
