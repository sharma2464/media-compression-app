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
        // Keep preview bitmap dimensions identical to [original] so wipe compare crops align.
        val (fullW, fullH) = VideoMetadataProbe.targetDimensions(meta, settings.resolution)
        val willShrink = min(
            fullW.toFloat() / meta.width.coerceAtLeast(1),
            fullH.toFloat() / meta.height.coerceAtLeast(1),
        ) < 1f
        val quality = if (willShrink) 45 else 40
        return jpegDegrade(original, quality)
    }

    private fun jpegDegrade(source: Bitmap, quality: Int): Bitmap {
        val stream = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 85), stream)
        val bytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }
}
