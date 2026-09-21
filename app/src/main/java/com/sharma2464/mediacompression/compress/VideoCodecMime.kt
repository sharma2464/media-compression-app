package com.sharma2464.mediacompression.compress

import android.media.MediaCodecList
import androidx.media3.common.MimeTypes

object VideoCodecMime {
    fun VideoCodec.toMime(): String = when (this) {
        VideoCodec.H264 -> MimeTypes.VIDEO_H264
        VideoCodec.H265 -> MimeTypes.VIDEO_H265
        VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
        VideoCodec.VP9 -> MimeTypes.VIDEO_VP9
        VideoCodec.VP8 -> MimeTypes.VIDEO_VP8
        VideoCodec.MPEG4 -> MimeTypes.VIDEO_MP4V
    }

    fun codecFromMime(mime: String): VideoCodec = when (mime.lowercase()) {
        MimeTypes.VIDEO_H264 -> VideoCodec.H264
        MimeTypes.VIDEO_H265 -> VideoCodec.H265
        MimeTypes.VIDEO_AV1 -> VideoCodec.AV1
        MimeTypes.VIDEO_VP9 -> VideoCodec.VP9
        MimeTypes.VIDEO_VP8 -> VideoCodec.VP8
        MimeTypes.VIDEO_MP4V -> VideoCodec.MPEG4
        else -> VideoCodec.H265
    }

    fun labelForMime(mime: String): String = when (mime.lowercase()) {
        MimeTypes.VIDEO_H265 -> "H.265 (efficient)"
        MimeTypes.VIDEO_H264 -> "H.264 (compatible)"
        MimeTypes.VIDEO_AV1 -> "AV1 (high efficiency)"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_MP4V -> "MPEG-4"
        else -> mime.substringAfter("/").uppercase()
    }

    fun hasEncoder(mime: String): Boolean {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun deviceVideoEncoders(): List<String> {
        val codecs = mutableSetOf<String>()
        try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.startsWith("video/", ignoreCase = true)) {
                        codecs.add(type.lowercase())
                    }
                }
            }
        } catch (_: Exception) {
        }
        return codecs.sorted()
    }

    fun supportedCodecs(allCodecsEnabled: Boolean): List<String> {
        if (allCodecsEnabled) return deviceVideoEncoders()
        val supported = mutableListOf(MimeTypes.VIDEO_H264)
        if (hasEncoder(MimeTypes.VIDEO_H265)) supported.add(MimeTypes.VIDEO_H265)
        if (hasEncoder(MimeTypes.VIDEO_AV1)) supported.add(MimeTypes.VIDEO_AV1)
        return supported.distinct()
    }

    /** AV1 → H.265 → H.264 using device encoder availability (or [supported] when provided). */
    fun pickEfficientMime(allCodecsEnabled: Boolean, supported: List<String>? = null): String {
        val codecs = supported ?: supportedCodecs(allCodecsEnabled)
        if (codecs.any { it.equals(MimeTypes.VIDEO_AV1, ignoreCase = true) }) return MimeTypes.VIDEO_AV1
        if (codecs.any { it.equals(MimeTypes.VIDEO_H265, ignoreCase = true) }) return MimeTypes.VIDEO_H265
        return MimeTypes.VIDEO_H264
    }
}

fun CompressJobSettings.effectiveVideoMime(): String =
    videoCodecMime?.takeIf { it.isNotBlank() } ?: VideoCodecMime.run { videoCodec.toMime() }
