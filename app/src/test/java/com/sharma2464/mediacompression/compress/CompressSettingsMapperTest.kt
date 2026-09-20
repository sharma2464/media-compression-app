package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.settings.CompressionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressSettingsMapperTest {

    @Test
    fun default_maps_to_balanced_strength() {
        val profile = CompressSettingsMapper.toProfile(CompressionMode.ADAPTIVE, CompressJobSettings.DEFAULT, null)
        assertEquals(CompressionStrength.BALANCED, profile.strength)
    }

    @Test
    fun low_preset_prefers_smaller_bitrate() {
        val low = CompressJobSettings(presetTier = PresetTier.LOW, qualitySlider = 20)
        val high = CompressJobSettings(presetTier = PresetTier.HIGH, qualitySlider = 80)
        val pLow = CompressSettingsMapper.toProfile(CompressionMode.ADAPTIVE, low, null)
        val pHigh = CompressSettingsMapper.toProfile(CompressionMode.ADAPTIVE, high, null)
        assertTrue(pLow.videoBitrateFactor < pHigh.videoBitrateFactor)
    }

    @Test
    fun platform_target_enables_ffmpeg() {
        val settings = CompressJobSettings(platformTarget = PlatformPreset.DISCORD)
        val meta = VideoMetadata(1920, 1080, 60_000, 5_000_000, 30f)
        val profile = CompressSettingsMapper.toProfile(CompressionMode.ADAPTIVE, settings, meta)
        assertTrue(profile.preferFfmpeg)
    }

    @Test
    fun summaryLabel_includes_tier() {
        val label = CompressSettingsMapper.summaryLabel(CompressJobSettings.DEFAULT)
        assertTrue(label.contains("Medium"))
    }
}
