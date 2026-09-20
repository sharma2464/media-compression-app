package com.sharma2464.mediacompression.compress

import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PhotoEncodePlanner {
    data class PhotoEncodePlan(
        val maxLongEdge: Int,
        val webpQuality: Int,
    )

    fun plan(input: File, settings: CompressJobSettings): PhotoEncodePlan {
        if (!input.isFile) {
            return PhotoEncodePlan(maxLongEdge = 2048, webpQuality = qualityForTargetMb(settings.targetSizeMb))
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.path, options)
        val w = options.outWidth.coerceAtLeast(1)
        val h = options.outHeight.coerceAtLeast(1)
        val longEdge = max(w, h)
        val originalBytes = input.length().coerceAtLeast(1L)
        val targetBytes = (settings.targetSizeMb * 1024 * 1024).toLong().coerceAtLeast(32_768L)

        var maxEdge = longEdge
        if (targetBytes < originalBytes) {
            val ratio = sqrt(targetBytes.toDouble() / originalBytes).coerceIn(0.15, 1.0)
            maxEdge = (longEdge * ratio).roundToInt().coerceIn(480, longEdge)
            when {
                targetBytes < 500_000 -> maxEdge = min(maxEdge, 1280)
                targetBytes < 2_000_000 -> maxEdge = min(maxEdge, 1920)
                targetBytes < 8_000_000 -> maxEdge = min(maxEdge, 2560)
            }
        }

        val sizeRatio = targetBytes.toDouble() / originalBytes
        val quality = when {
            sizeRatio >= 0.85 -> 88
            sizeRatio >= 0.55 -> 78
            sizeRatio >= 0.35 -> 65
            sizeRatio >= 0.18 -> 52
            sizeRatio >= 0.08 -> 40
            else -> 32
        }.coerceIn(28, 92)

        return PhotoEncodePlan(maxLongEdge = maxEdge, webpQuality = quality)
    }

    private fun qualityForTargetMb(targetSizeMb: Float): Int = when {
        targetSizeMb <= 1f -> 40
        targetSizeMb <= 4f -> 52
        targetSizeMb <= 12f -> 65
        else -> 78
    }
}
