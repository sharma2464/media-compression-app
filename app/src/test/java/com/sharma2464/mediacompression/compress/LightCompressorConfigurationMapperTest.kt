package com.sharma2464.mediacompression.compress

import com.abedelazizshe.lightcompressorlibrary.VideoCodec as LcVideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCompressorConfigurationMapperTest {

    @Test
    fun maps_target_bitrate_and_disables_min_check() {
        val profile = CompressionProfile(
            mode = com.sharma2464.mediacompression.settings.CompressionMode.ADAPTIVE,
            strength = CompressionStrength.BALANCED,
            targetVideoBitrateBps = 1_200_000,
            removeAudio = true,
            videoCodec = VideoCodec.H264,
            outputVideoHeight = 720,
        )
        val config = LightCompressorConfigurationMapper.toConfiguration(profile, "out.mp4")
        assertEquals(1_200_000L, config.videoBitrateInBps)
        assertFalse(config.isMinBitrateCheckEnabled)
        assertTrue(config.disableAudio)
        assertEquals(LcVideoCodec.H264, config.videoCodec)
        assertEquals(listOf("out.mp4"), config.videoNames)
    }

    @Test
    fun h265_when_not_h264() {
        val profile = CompressionProfile(
            mode = com.sharma2464.mediacompression.settings.CompressionMode.ADAPTIVE,
            strength = CompressionStrength.BALANCED,
            videoCodec = VideoCodec.H265,
        )
        val config = LightCompressorConfigurationMapper.toConfiguration(profile, "x")
        assertEquals(LcVideoCodec.H265, config.videoCodec)
    }

    @Test
    fun min_bitrate_check_when_no_explicit_bitrate() {
        val profile = CompressionProfile(
            mode = com.sharma2464.mediacompression.settings.CompressionMode.ADAPTIVE,
            strength = CompressionStrength.BALANCED,
            targetVideoBitrateBps = null,
        )
        val config = LightCompressorConfigurationMapper.toConfiguration(profile, "x")
        assertTrue(config.isMinBitrateCheckEnabled)
        assertNull(config.videoBitrateInBps)
    }
}
