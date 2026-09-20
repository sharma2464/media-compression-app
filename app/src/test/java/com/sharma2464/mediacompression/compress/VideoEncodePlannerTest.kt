package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEncodePlannerTest {

    @Test
    fun targetBitrate_respects_overhead_and_audio() {
        val meta = VideoMetadata(3840, 2160, 60_000, 50_000_000, 60f)
        val settings = CompressJobSettings(
            platformTarget = PlatformPreset.DISCORD,
            resolution = ResolutionChoice.ORIGINAL,
            frameRate = FrameRateChoice.ORIGINAL,
        )
        val planned = VideoEncodePlanner.planWithDuration(
            file = java.io.File("/nonexistent"),
            settings = settings,
            videoMeta = meta,
            targetBytes = PlatformPreset.DISCORD.maxBytes,
        )
        assertTrue(planned.videoBitrateBps > 200_000)
        assertTrue(planned.outputVideoHeight == 0 || planned.outputVideoHeight <= 2160)
        assertTrue(planned.outputFps == null || planned.outputFps == 30)
    }
}
