package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File
import java.io.FileOutputStream

/**
 * WebP re-encode via the platform's native Bitmap/WebP codec — no third-party
 * image library needed. EXIF is read from the original and reapplied to the output.
 */
class PhotoCompressor : Compressor {
    override suspend fun compress(input: File, profile: CompressionProfile, workDir: File, onProgress: (Int) -> Unit): CompressionResult {
        val mode = profile.mode
        val bitmap = BitmapFactory.decodeFile(input.path)
            ?: error("Could not decode image: ${input.path}")

        val output = File(workDir, "${input.nameWithoutExtension}.webp")
        val lossless = mode == CompressionMode.LOSSLESS_ONLY

        FileOutputStream(output).use { out ->
            val format = if (lossless) LOSSLESS_FORMAT else LOSSY_FORMAT
            val quality = if (lossless) 100 else profile.photoWebpQuality
            bitmap.compress(format, quality, out)
        }
        bitmap.recycle()

        // Adaptive mode falls back to the lossless pass if lossy didn't actually shrink it
        val finalLossless = lossless || output.length() >= input.length()
        val finalOutput = if (!lossless && finalLossless) {
            FileOutputStream(output).use { out -> BitmapFactory.decodeFile(input.path)!!.compress(LOSSLESS_FORMAT, 100, out) }
            output
        } else output

        ExifPreserver.copy(input, finalOutput)
        return CompressionResult(finalOutput, finalLossless)
    }

    companion object {
        @Suppress("DEPRECATION")
        private val LOSSLESS_FORMAT = Bitmap.CompressFormat.WEBP_LOSSLESS
        @Suppress("DEPRECATION")
        private val LOSSY_FORMAT = Bitmap.CompressFormat.WEBP_LOSSY
    }
}
