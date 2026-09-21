package com.sharma2464.mediacompression.compress

import androidx.media3.common.MimeTypes
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

    @Test
    fun long_4k_clip_can_target_140mb() {
        val durationMs = 20 * 60 * 1000L
        val meta = VideoMetadata(3840, 2160, durationMs, 20_000_000, 30f)
        val settings = CompressJobSettings(
            targetSizeMb = 140f,
            videoCodec = VideoCodec.H265,
            resolution = ResolutionChoice.ORIGINAL,
        )
        val planned = VideoEncodePlanner.planWithDuration(
            file = java.io.File("/nonexistent"),
            settings = settings,
            videoMeta = meta,
            targetBytes = (140L * 1024 * 1024),
        )
        assertTrue(
            "bitrate=${planned.videoBitrateBps} height=${planned.outputVideoHeight}",
            planned.videoBitrateBps < 1_200_000,
        )
        assertTrue(planned.outputVideoHeight > 0 && planned.outputVideoHeight <= 2160)
    }

    @Test
    fun av1_target_uses_lower_floor_than_h264_at_same_size() {
        val meta = VideoMetadata(1920, 1080, 60_000, 8_000_000, 30f)
        val targetBytes = (15L * 1024 * 1024)
        val av1 = VideoEncodePlanner.planWithDuration(
            file = java.io.File("/nonexistent"),
            settings = CompressJobSettings(
                targetSizeMb = 15f,
                videoCodecMime = MimeTypes.VIDEO_AV1,
                videoCodec = VideoCodec.AV1,
            ),
            videoMeta = meta,
            targetBytes = targetBytes,
        )
        val h264 = VideoEncodePlanner.planWithDuration(
            file = java.io.File("/nonexistent"),
            settings = CompressJobSettings(
                targetSizeMb = 15f,
                videoCodecMime = MimeTypes.VIDEO_H264,
                videoCodec = VideoCodec.H264,
            ),
            videoMeta = meta,
            targetBytes = targetBytes,
        )
        assertTrue(av1.videoBitrateBps <= h264.videoBitrateBps)
    }
}
