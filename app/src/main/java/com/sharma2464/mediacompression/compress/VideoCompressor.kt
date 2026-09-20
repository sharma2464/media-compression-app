package com.sharma2464.mediacompression.compress

import android.content.Context
import java.io.File

/** Hardware-accelerated video transcode via Media3 Transformer (same approach as Compressor). */
class VideoCompressor(private val context: Context) : Compressor {
    private val media3 = Media3VideoCompressor(context)

    override suspend fun compress(
        input: File,
        profile: CompressionProfile,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult = media3.compress(input, profile, workDir, onProgress)
}
