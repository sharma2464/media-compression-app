package com.sharma2464.mediacompression.compress

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
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
    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        if (mode == CompressionMode.LOSSLESS_ONLY) {
            val output = File(workDir, input.name)
            input.copyTo(output, overwrite = true)
            return CompressionResult(output, wasLossless = true)
        }

        val output = File(workDir, "${input.nameWithoutExtension}_compressed.mp4")
        transcode(input, output)
        return CompressionResult(output, wasLossless = false)
    }

    private suspend fun transcode(input: File, output: File) = suspendCancellableCoroutine<Unit> { cont ->
        val transformer = Transformer.Builder(context)
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
        cont.invokeOnCancellation { transformer.cancel() }
    }
}
