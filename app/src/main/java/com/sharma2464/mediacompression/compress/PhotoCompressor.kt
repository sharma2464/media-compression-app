package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * WebP/JPEG re-encode via platform Bitmap codecs. EXIF copied from the original.
 */
class PhotoCompressor : Compressor {
    override suspend fun compress(
        input: File,
        profile: CompressionProfile,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult {
        onProgress(5)
        val mode = profile.mode
        val lossless = mode == CompressionMode.LOSSLESS_ONLY
        // Lossy output is WebP so EXIF can be re-applied reliably (JPEG from Bitmap strips tags).
        val outputName = "${input.nameWithoutExtension}.webp"
        val useJpeg = false
        val output = File(workDir, outputName)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.path, bounds)
        val srcW = bounds.outWidth.coerceAtLeast(1)
        val srcH = bounds.outHeight.coerceAtLeast(1)
        val longEdge = max(srcW, srcH)
        val maxEdge = profile.maxPhotoLongEdge?.coerceAtLeast(64) ?: longEdge
        val sample = computeInSampleSize(srcW, srcH, maxEdge)

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(input.path, decodeOpts)
            ?: error("Could not decode image: ${input.path}")
        onProgress(25)

        val scaledLong = max(bitmap.width, bitmap.height)
        if (scaledLong > maxEdge) {
            val scale = maxEdge.toFloat() / scaledLong
            val matrix = Matrix().apply { setScale(scale, scale) }
            val resized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (resized != bitmap) bitmap.recycle()
            bitmap = resized
        }
        onProgress(45)

        val quality = if (lossless) 100 else profile.photoWebpQuality.coerceIn(28, 100)
        writeBitmap(bitmap, output, useJpeg, lossless, quality)
        bitmap.recycle()
        onProgress(70)

        var finalLossless = lossless
        if (!lossless && output.length() >= input.length() && quality > 32) {
            val retryBitmap = BitmapFactory.decodeFile(input.path, decodeOpts)
            if (retryBitmap != null) {
                val retryQ = (quality - 15).coerceAtLeast(28)
                writeBitmap(retryBitmap, output, useJpeg, false, retryQ)
                retryBitmap.recycle()
            }
        }
        if (!lossless && output.length() >= input.length()) {
            finalLossless = true
        }

        ExifPreserver.copy(input, output)
        onProgress(100)
        return CompressionResult(output, finalLossless)
    }

    private fun writeBitmap(
        bitmap: Bitmap,
        output: File,
        useJpeg: Boolean,
        lossless: Boolean,
        quality: Int,
    ) {
        FileOutputStream(output).use { out ->
            when {
                useJpeg -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                lossless -> bitmap.compress(webpLosslessFormat(), 100, out)
                else -> bitmap.compress(webpLossyFormat(), quality, out)
            }
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxLongEdge: Int): Int {
        var sample = 1
        val longEdge = max(width, height)
        while (longEdge / sample > maxLongEdge * 1.2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    companion object {
        private fun webpLossyFormat(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP_LOSSY
            }

        private fun webpLosslessFormat(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP_LOSSLESS
            }
    }
}
