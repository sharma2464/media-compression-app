package com.sharma2464.mediacompression.compress

import android.content.Context
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.VideoEngine
import java.io.File

/** Video transcode via Media3 (default) or optional LightCompressor-enhanced. */
class VideoCompressor(private val context: Context) : Compressor {
    private val media3 = Media3VideoCompressor(context)
    private val lightCompressor = LightCompressorVideoAdapter(context)
    private val settings = AppSettings(context)

    override suspend fun compress(
        input: File,
        profile: CompressionProfile,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult {
        return when (settings.videoEngine) {
            VideoEngine.LIGHT_COMPRESSOR -> lightCompressor.compress(input, profile, workDir, onProgress)
            VideoEngine.MEDIA3 -> media3.compress(input, profile, workDir, onProgress)
        }
    }
}
