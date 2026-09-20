package com.sharma2464.mediacompression.compress

import kotlin.math.min

/**
 * Resolution helpers aligned with Josh Atticus Compressor (MIT).
 * https://github.com/JoshAtticus/Compressor
 */
object VideoDimensions {
    /** Maps a short-side target to stored [MediaFormat] height. */
    fun outputHeightForShortSide(originalWidth: Int, originalHeight: Int, shortSide: Int): Int {
        if (shortSide <= 0 || originalWidth <= 0 || originalHeight <= 0) return originalHeight
        val isVertical = originalHeight > originalWidth
        return if (isVertical) {
            val targetWidth = min(shortSide, originalWidth)
            ((targetWidth.toLong() * originalHeight + originalWidth - 1) / originalWidth).toInt()
        } else {
            min(shortSide, originalHeight)
        }
    }

    fun shortSideForOutputHeight(originalWidth: Int, originalHeight: Int, outputHeight: Int): Int {
        if (outputHeight <= 0) return min(originalWidth, originalHeight)
        val isVertical = originalHeight > originalWidth
        return if (isVertical && originalWidth > 0) {
            outputHeight * originalWidth / originalHeight
        } else {
            outputHeight
        }
    }

    /** Presentation size for Transformer (width × height in coded coordinates). */
    fun presentationSize(originalWidth: Int, originalHeight: Int, outputHeight: Int): Pair<Int, Int>? {
        if (outputHeight <= 0 || originalHeight <= 0 || outputHeight >= originalHeight) return null
        val aspect = originalWidth.toFloat() / originalHeight
        var width = (outputHeight * aspect).toInt()
        var height = outputHeight
        if (width % 2 != 0) width -= 1
        if (height % 2 != 0) height -= 1
        if (width <= 0 || height <= 0) return null
        return width to height
    }
}
