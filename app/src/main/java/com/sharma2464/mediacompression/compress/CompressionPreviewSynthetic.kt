package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Stand-in compressed preview while the muxer output is not yet readable (typical during encode).
 */
object CompressionPreviewSynthetic {
    fun fromOriginal(
        original: Bitmap,
        meta: VideoMetadata,
        settings: CompressJobSettings,
    ): Bitmap {
        val (targetW, targetH) = VideoMetadataProbe.targetDimensions(meta, settings.resolution)
        if (targetW <= 0 || targetH <= 0) return jpegDegrade(original, 42)

        if (targetW < original.width || targetH < original.height) {
            val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
            return jpegDegrade(scaled, 48).also {
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
