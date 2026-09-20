package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressTargetSizingTest {

    @Test
    fun tiny_file_does_not_crash_coerce() {
        assertEquals(0.5f, initialCompressTargetMb(1L, hasVideo = true, videoOrPhotoRatio = 0.7f))
    }

    @Test
    fun large_video_uses_ratio_of_original() {
        val bytes = (2.6 * 1024 * 1024 * 1024).toLong()
        val mb = initialCompressTargetMb(bytes, hasVideo = true, videoOrPhotoRatio = 0.7f)
        assertTrue(mb > 1800f && mb < 1900f)
    }

    @Test
    fun aggressive_target_140mb_from_ratio() {
        val bytes = (2.6 * 1024 * 1024 * 1024).toLong()
        val ratio = 140f / (bytes / (1024f * 1024f))
        val mb = initialCompressTargetMb(bytes, hasVideo = true, videoOrPhotoRatio = ratio)
        assertEquals(140f, mb, 1f)
    }
}
