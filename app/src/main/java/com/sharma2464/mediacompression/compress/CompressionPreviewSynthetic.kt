package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap

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
        if (targetW <= 0 || targetH <= 0) return original

        if (targetW < original.width || targetH < original.height) {
            return Bitmap.createScaledBitmap(original, targetW, targetH, true)
        }
        // Same resolution target: downscale-up simulates softer re-encode until real output is available.
        val midW = (original.width * 0.72f).toInt().coerceAtLeast(1)
        val midH = (original.height * 0.72f).toInt().coerceAtLeast(1)
        val mid = Bitmap.createScaledBitmap(original, midW, midH, true)
        return Bitmap.createScaledBitmap(mid, original.width, original.height, true).also {
            if (mid != original) mid.recycle()
        }
    }
}
