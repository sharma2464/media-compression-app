package com.sharma2464.mediacompression.compress

import android.content.Context
import android.net.Uri
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class LightCompressorVideoAdapter(private val context: Context) {

    suspend fun compress(
        input: File,
        profile: CompressionProfile,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val outputName = "${input.nameWithoutExtension}_compressed.mp4"
            val config = LightCompressorConfigurationMapper.toConfiguration(profile, outputName)
            val storage = WorkDirStorageConfiguration(workDir)
            val uri = Uri.fromFile(input)

            cont.invokeOnCancellation {
                VideoCompressor.cancel()
            }

            VideoCompressor.start(
                context = context.applicationContext,
                uris = listOf(uri),
                isStreamable = false,
                storageConfiguration = storage,
                configureWith = config,
                listener = object : CompressionListener {
                    override fun onStart(index: Int) {
                        onProgress(0)
                    }

                    override fun onProgress(index: Int, percent: Float) {
                        onProgress(percent.toInt().coerceIn(0, 100))
                    }

                    override fun onSuccess(index: Int, size: Long, path: String?) {
                        val out = path?.let { File(it) } ?: File(workDir, outputName)
                        if (cont.isActive) {
                            cont.resume(CompressionResult(out, wasLossless = false))
                        }
                    }

                    override fun onFailure(index: Int, failureMessage: String) {
                        if (cont.isActive) {
                            cont.resumeWithException(IllegalStateException(failureMessage))
                        }
                    }

                    override fun onCancelled(index: Int) {
                        if (cont.isActive) {
                            cont.resumeWithException(kotlinx.coroutines.CancellationException("LightCompressor cancelled"))
                        }
                    }
                },
            )
        }
    }
}
