package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Stand-in compressed preview while the muxer output is not yet readable (typical during encode).
 * Sizes relative to the preview [original] bitmap (downscaled), not full file resolution.
 */
object CompressionPreviewSynthetic {
    fun fromOriginal(
        original: Bitmap,
        meta: VideoMetadata,
        settings: CompressJobSettings,
    ): Bitmap {
        val (fullW, fullH) = VideoMetadataProbe.targetDimensions(meta, settings.resolution)
        val scale = min(
            fullW.toFloat() / meta.width.coerceAtLeast(1),
            fullH.toFloat() / meta.height.coerceAtLeast(1),
        ).coerceAtMost(1f)
        val tw = (original.width * scale).toInt().coerceAtLeast(1)
        val th = (original.height * scale).toInt().coerceAtLeast(1)
        if (tw < original.width || th < original.height) {
            val scaled = Bitmap.createScaledBitmap(original, tw, th, true)
            return jpegDegrade(scaled, 45).also {
                if (scaled != original) scaled.recycle()
            }
        }
        return jpegDegrade(original, 40)
    }

    private fun jpegDegrade(source: Bitmap, quality: Int): Bitmap {
        val stream = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 85), stream)
        val bytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }
}
