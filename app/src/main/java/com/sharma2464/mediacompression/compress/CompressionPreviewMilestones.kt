package com.sharma2464.mediacompression.compress

object CompressionPreviewMilestones {
    const val PERCENT_STEP = 5

    fun shouldCaptureMilestone(percent: Int, lastCapturedPercent: Int): Boolean {
        if (percent >= 100) return lastCapturedPercent < 100
        if (lastCapturedPercent < 0) return true
        return percent - lastCapturedPercent >= PERCENT_STEP
    }

    fun stepFrameIndex(currentIndex: Int, lastGeneratedIndex: Int, delta: Int): Int? {
        val next = currentIndex + delta
        if (next < 0 || next > lastGeneratedIndex) return null
        return next
    }
}
