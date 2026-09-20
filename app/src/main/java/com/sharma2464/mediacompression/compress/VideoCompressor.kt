package com.sharma2464.mediacompression.compress

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.VideoEncoderSettings
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

/**
 * Hardware-accelerated transcode via Media3 Transformer (MediaCodec under the hood).
 *
 * ponytail: there's no practical lossless video codec on Android hardware encoders, so
 * LOSSLESS_ONLY mode here just copies the file through untouched (0% saving, reported
 * honestly) rather than faking a "lossless" re-encode. Upgrade path: FFV1/lossless H.264
 * via a software encoder if that's ever actually needed.
 */
class VideoCompressor(private val context: Context) : Compressor {
    override suspend fun compress(
        input: File,
        mode: CompressionMode,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult {
        if (mode == CompressionMode.LOSSLESS_ONLY) {
            val output = File(workDir, input.name)
            input.copyTo(output, overwrite = true)
            onProgress(100)
            return CompressionResult(output, wasLossless = true)
        }

        val output = File(workDir, "${input.nameWithoutExtension}_compressed.mp4")
        return try {
            transcode(input, output, onProgress)
            CompressionResult(output, wasLossless = false)
        } catch (e: IOException) {
            // Known Media3 export deadlock (androidx/media#3357) — some VFR videos with
            // sample gaps will never finish re-encoding no matter how many times it's retried.
            // Fall back to a straight copy so the file still reaches the destination folder.
            output.delete()
            val passthrough = File(workDir, input.name)
            input.copyTo(passthrough, overwrite = true)
            onProgress(100)
            CompressionResult(passthrough, wasLossless = true)
        }
    }

    // Transformer must be built/started/cancelled from a thread with a prepared Looper
    // (the main thread) — calling it from WorkManager's background dispatcher throws
    // silently (swallowed by CompressionWorker's runCatching), which left videos stuck
    // forever at Decision.COMPRESS and never reaching the gallery.
    // Observed in the field (matches androidx/media#3357): Media3 1.4.0's Transformer can
    // permanently deadlock mid-export — no more samples ever arrive from the asset loader,
    // getProgress() freezes (we saw 99% forever, 0% CPU), and onCompleted/onError never fire.
    // A flat overall timeout wouldn't help fast files finish sooner or slow files at all, so
    // instead the poll loop below fails the export once *progress itself* stops moving.
    private suspend fun transcode(input: File, output: File, onProgress: (Int) -> Unit) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            // Requesting H.265 forces an actual re-encode instead of Transformer's default
            // passthrough remux (which left file size ~unchanged); the bitrate target is what
            // does the real shrinking.
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(targetBitrate(input)).build())
                .build()
            val transformer = Transformer.Builder(context)
                .setTransformationRequest(TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H265).build())
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: androidx.media3.transformer.ExportResult) {
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }
                    override fun onError(
                        composition: Composition,
                        result: androidx.media3.transformer.ExportResult,
                        exception: androidx.media3.transformer.ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                })
                .build()
            val mediaItem = MediaItem.fromUri(input.toURI().toString())
            transformer.start(EditedMediaItem.Builder(mediaItem).build(), output.path)

            // Transformer.getProgress() must also be polled from the Looper thread it was
            // built on (main), hence this coroutine living in Dispatchers.Main rather than
            // a separate polling thread.
            val pollJob = launch {
                val holder = ProgressHolder()
                // Signature is (state, progress) combined, not just progress: once the export
                // deadlocks, getProgress() can stop reporting PROGRESS_STATE_AVAILABLE entirely
                // (flipping to e.g. UNAVAILABLE) — gating the tracker on AVAILABLE-only meant a
                // stall that also changes *state* (not just progress) was never detected, and
                // the UI was left showing a stale last-known percent (e.g. "99%") forever.
                val stallTracker = StallTracker(STALL_TIMEOUT_MS)
                var lastKnownPercent = 0
                while (isActive) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        lastKnownPercent = holder.progress
                        onProgress(holder.progress)
                    }
                    val stalled = stallTracker.onProgress(state * 1000 + lastKnownPercent)
                    if (stalled) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException(
                                    "Transformer stalled at $lastKnownPercent% (state=$state, " +
                                        "no change for ${STALL_TIMEOUT_MS / 1000}s) — known Media3 export deadlock",
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

    // ponytail: heuristic target (30% of source bitrate, floor 300 kbps) rather than a
    // perceptual-quality encoder pass — good enough for adaptive mode's "honest savings" goal.
    // Upgrade path: two-pass VMAF-targeted encode if quality complaints show up.
    private fun targetBitrate(input: File): Int {
        val retriever = MediaMetadataRetriever()
        val sourceBitrate = try {
            retriever.setDataSource(input.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        } finally {
            retriever.release()
        } ?: DEFAULT_SOURCE_BITRATE
        return (sourceBitrate * BITRATE_FACTOR).toInt().coerceAtLeast(MIN_BITRATE)
    }

    private companion object {
        const val BITRATE_FACTOR = 0.3
        const val MIN_BITRATE = 300_000
        const val DEFAULT_SOURCE_BITRATE = 4_000_000
        const val STALL_TIMEOUT_MS = 60_000L
    }
}
