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
}
