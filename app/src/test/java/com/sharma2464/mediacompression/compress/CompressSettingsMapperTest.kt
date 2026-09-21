package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.settings.CompressionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressSettingsMapperTest {

    @Test
    fun default_best_preset_maps_to_small_strength() {
        val profile = CompressSettingsMapper.toProfile(CompressionMode.ADAPTIVE, CompressJobSettings.DEFAULT, null)
        assertEquals(CompressionStrength.SMALL, profile.strength)
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
    fun summaryLabel_includes_tier() {
        val label = CompressSettingsMapper.summaryLabel(CompressJobSettings.DEFAULT)
        assertTrue(label.contains("Best"))
    }

    @Test
    fun low_target_size_mb_lowers_photo_quality() {
        val job = CompressJobSettings(qualitySlider = 50, targetSizeMb = 1f)
        val profile = CompressSettingsMapper.toProfile(
            CompressionMode.ADAPTIVE,
            job,
            null,
            java.io.File("/fake/photo.jpg"),
        )
        assertTrue(profile.photoWebpQuality <= 55)
        assertTrue(profile.maxPhotoLongEdge != null)
    }
}
