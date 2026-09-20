package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionBatchProgressTest {

    @Test
    fun overall_fraction_counts_partial_current_file() {
        val batch = CompressionBatch(
            files = listOf(
                FileProgress("a.mp4", 100, FileCompressionState.DONE),
                FileProgress("b.mp4", 100, FileCompressionState.IN_PROGRESS, percent = 50),
                FileProgress("c.mp4", 100, FileCompressionState.QUEUED),
            ),
            currentIndex = 1,
            modeLabel = "Adaptive",
        )
        assertEquals(0.5f, batchOverallFraction(batch))
    }

    @Test
    fun overall_fraction_all_done_is_one() {
        val batch = CompressionBatch(
            files = listOf(
                FileProgress("a.mp4", 100, FileCompressionState.DONE),
                FileProgress("b.mp4", 100, FileCompressionState.DONE),
            ),
            currentIndex = 1,
            modeLabel = "Adaptive",
        )
        assertEquals(1f, batchOverallFraction(batch))
    }
}
