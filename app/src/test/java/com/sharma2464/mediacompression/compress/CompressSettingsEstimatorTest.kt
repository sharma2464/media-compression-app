package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompressSettingsEstimatorTest {

    @Test
    fun adaptive_estimate_is_below_original_for_video() {
        val tmp = File.createTempFile("est", ".bin")
        tmp.writeBytes(ByteArray(50_000))
        val pairs = listOf(tmp to FileKind.VIDEO)
        val est = CompressSettingsEstimator.estimatedBytesAfter(
            pairs,
            CompressionMode.ADAPTIVE,
            CompressJobSettings(presetTier = PresetTier.MEDIUM),
            primaryVideo = null,
        )
        tmp.delete()
        assertTrue(est < 50_000)
    }

    @Test
    fun target_cap_limits_batch_estimate() {
        val tmp = File.createTempFile("est", ".bin")
        tmp.writeBytes(ByteArray(10_000_000))
        val settings = CompressJobSettings(targetSizeMb = 5f, presetTier = PresetTier.MEDIUM)
        val est = CompressSettingsEstimator.estimatedBytesAfter(
            listOf(tmp to FileKind.VIDEO),
            CompressionMode.ADAPTIVE,
            settings,
            primaryVideo = null,
        )
        tmp.delete()
        assertTrue(est <= 5f * 1024 * 1024)
    }
}
