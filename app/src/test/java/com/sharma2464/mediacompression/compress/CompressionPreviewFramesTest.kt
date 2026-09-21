package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPreviewFramesTest {

    @Test
    fun encodedSeekTimeUs_clamps_to_encode_progress() {
        val sourceDurationMs = 10_000L
        val us = CompressionPreviewFrames.encodedSeekTimeUs(
            sourceDurationMs = sourceDurationMs,
            timelinePercent = 80,
            encodeProgressPercent = 40,
            encodedDurationMs = null,
        )
        // 40% of 10s = 4s cap; 80% timeline would be 8s but clamped to ~3.68s
        assertTrue(us <= 4_000_000L)
        assertTrue(us > 0L)
    }

    @Test
    fun compareSeekTimeUs_caps_original_during_encode() {
        val capped = CompressionPreviewFrames.compareSeekTimeUs(
            sourceDurationMs = 10_000L,
            timelinePercent = 80,
            encodeProgressPercent = 40,
            encodedDurationMs = null,
            useEncodeCap = true,
        )
        val full = CompressionPreviewFrames.compareSeekTimeUs(
            sourceDurationMs = 10_000L,
            timelinePercent = 80,
            encodeProgressPercent = 40,
            encodedDurationMs = null,
            useEncodeCap = false,
        )
        assertTrue(capped < full)
    }

    @Test
    fun encodedSeekTimeUs_respects_encoded_duration_metadata() {
        val us = CompressionPreviewFrames.encodedSeekTimeUs(
            sourceDurationMs = 20_000L,
            timelinePercent = 50,
            encodeProgressPercent = 100,
            encodedDurationMs = 5_000L,
        )
        assertEquals(4_600_000L, us)
    }
}
