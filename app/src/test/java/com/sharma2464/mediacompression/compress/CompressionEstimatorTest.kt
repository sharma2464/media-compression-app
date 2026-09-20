package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.compress.CompressionStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompressionEstimatorTest {

    @Test
    fun adaptive_video_estimates_below_original_size() {
        val file = tempFile(sizeBytes = 1_000_000)
        val estimated = CompressionEstimator.estimatedBytesAfter(file, FileKind.VIDEO, CompressionMode.ADAPTIVE)
        assertTrue(estimated < 1_000_000)
        assertEquals(540_000L, estimated)
        file.delete()
    }

    @Test
    fun lossless_photo_near_original_size() {
        val file = tempFile(sizeBytes = 10_000)
        val estimated = CompressionEstimator.estimatedBytesAfter(file, FileKind.PHOTO, CompressionMode.LOSSLESS_ONLY)
        assertEquals(9_800L, estimated)
        file.delete()
    }

    @Test
    fun batch_sum_matches_individual() {
        val a = tempFile(sizeBytes = 1000)
        val b = tempFile(sizeBytes = 2000)
        val pairs = listOf(Pair(a, FileKind.VIDEO), Pair(b, FileKind.PHOTO))
        val total = CompressionEstimator.estimatedBytesAfter(pairs, CompressionMode.ADAPTIVE, CompressionStrength.BALANCED)
        val expected = CompressionEstimator.estimatedBytesAfter(a, FileKind.VIDEO, CompressionMode.ADAPTIVE) +
            CompressionEstimator.estimatedBytesAfter(b, FileKind.PHOTO, CompressionMode.ADAPTIVE)
        assertEquals(expected, total)
        a.delete()
        b.delete()
    }

    private fun tempFile(sizeBytes: Int): File {
        val f = File.createTempFile("est", ".bin")
        f.writeBytes(ByteArray(sizeBytes))
        return f
    }
}
