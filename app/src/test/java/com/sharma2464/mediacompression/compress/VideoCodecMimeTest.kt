package com.sharma2464.mediacompression.compress

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCodecMimeTest {

    @Test
    fun pickEfficientMime_prefers_av1_when_listed() {
        val mime = VideoCodecMime.pickEfficientMime(
            allCodecsEnabled = false,
            supported = listOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_AV1),
        )
        assertEquals(MimeTypes.VIDEO_AV1, mime)
    }

    @Test
    fun pickEfficientMime_falls_back_to_hevc() {
        val mime = VideoCodecMime.pickEfficientMime(
            allCodecsEnabled = false,
            supported = listOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265),
        )
        assertEquals(MimeTypes.VIDEO_H265, mime)
    }
}
