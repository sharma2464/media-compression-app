package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRateMappingTest {

    @Test
    fun target_fps_includes_10() {
        val meta = VideoMetadata(1920, 1080, 60_000, 5_000_000, 30f)
        assertEquals(10, VideoMetadataProbe.targetFps(meta, FrameRateChoice.FPS_10))
    }

    @Test
    fun profile_maps_10_fps() {
        val settings = CompressJobSettings(frameRate = FrameRateChoice.FPS_10)
        val meta = VideoMetadata(1280, 720, 10_000, 2_000_000, 30f)
        val profile = CompressSettingsMapper.toProfile(
            com.sharma2464.mediacompression.settings.CompressionMode.ADAPTIVE,
            settings,
            meta,
            null,
        )
        assertEquals(10, profile.targetFps)
    }
}
