package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertTrue
import org.junit.Test

class CompressJobSummaryTest {

    @Test
    fun lines_include_target_and_codec() {
        val settings = CompressJobSettings(
            targetSizeMb = 140f,
            videoCodec = VideoCodec.H265,
            presetTier = PresetTier.MEDIUM,
        )
        val lines = CompressJobSummary.lines(settings, "Adaptive · medium")
        assertTrue(lines.any { it.contains("140.0 MB") })
        assertTrue(lines.any { it.contains("H.265") })
        assertTrue(lines.any { it.contains("medium") })
    }
}
