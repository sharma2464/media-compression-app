package com.sharma2464.mediacompression.compress

import com.abedelazizshe.lightcompressorlibrary.VideoCodec as LcVideoCodec
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.VideoResizer

object LightCompressorConfigurationMapper {
    fun toConfiguration(
        profile: CompressionProfile,
        outputBaseName: String,
    ): Configuration {
        val resizer = profile.outputVideoHeight?.takeIf { it > 0 }?.let { h ->
            VideoResizer.limitShortSide(h.toDouble())
        } ?: profile.maxVideoLongEdge?.takeIf { it > 0 }?.let {
            VideoResizer.limitShortSide(it.toDouble())
        } ?: VideoResizer.auto

        val codec = when (profile.videoCodec) {
            VideoCodec.H264 -> LcVideoCodec.H264
            else -> LcVideoCodec.H265
        }

        val config = Configuration(
            quality = VideoQuality.MEDIUM,
            isMinBitrateCheckEnabled = profile.targetVideoBitrateBps == null,
            videoBitrateInMbps = null,
            videoBitrateInBps = profile.targetVideoBitrateBps?.toLong(),
            disableAudio = profile.removeAudio,
            resizer = resizer,
            videoNames = listOf(outputBaseName),
            videoCodec = codec,
        )
        return config
    }
}
