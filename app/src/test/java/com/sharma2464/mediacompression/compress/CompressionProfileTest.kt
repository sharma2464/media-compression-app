package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionProfileTest {

    @Test
    fun balanced_video_bitrate_factor_matches_legacy_default() {
        val profile = CompressionProfile.resolve(CompressionMode.ADAPTIVE, CompressionStrength.BALANCED)
        assertEquals(0.30, profile.videoBitrateFactor, 0.001)
    }

    @Test
    fun smallest_uses_lower_estimate_ratio_than_balanced_for_video() {
        val balanced = CompressionProfile.resolve(CompressionMode.ADAPTIVE, CompressionStrength.BALANCED)
        val smallest = CompressionProfile.resolve(CompressionMode.ADAPTIVE, CompressionStrength.SMALLEST)
        assertTrue(smallest.estimatedSizeRatio(FileKind.VIDEO) < balanced.estimatedSizeRatio(FileKind.VIDEO))
    }
}
