package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.settings.CompressionMode

object CompressSettingsMapper {
    fun toProfile(mode: CompressionMode, settings: CompressJobSettings, videoMeta: VideoMetadata?): CompressionProfile {
        if (mode == CompressionMode.LOSSLESS_ONLY) {
            return CompressionProfile.resolve(mode, CompressionStrength.BALANCED)
        }
        val tierFactor = when (settings.presetTier) {
            PresetTier.HIGH -> 0.45
            PresetTier.MEDIUM -> 0.30
            PresetTier.LOW -> 0.18
        }
        val sliderFactor = 0.12 + (100 - settings.qualitySlider.coerceIn(0, 100)) * 0.004
        var bitrateFactor = minOf(tierFactor, sliderFactor)

        settings.platformTarget?.let { platform ->
            if (videoMeta != null && videoMeta.durationMs > 0) {
                val durationSec = videoMeta.durationMs / 1000.0
                val audioKbps = settings.audioBitrateKbps ?: 128
                val audioBits = if (settings.removeAudio) 0 else audioKbps * 1000
                val maxVideoBps = ((platform.maxBytes * 8) / durationSec - audioBits).coerceAtLeast(200_000.0)
                val sourceBr = videoMeta.bitrateBps?.toDouble() ?: 4_000_000.0
                bitrateFactor = minOf(bitrateFactor, maxVideoBps / sourceBr)
            }
        }

        val useFfmpeg = preferFfmpeg(settings)
        val strength = when {
            useFfmpeg -> CompressionStrength.SMALLEST
            bitrateFactor <= 0.2 -> CompressionStrength.SMALL
            bitrateFactor <= 0.35 -> CompressionStrength.BALANCED
            else -> CompressionStrength.FAST
        }

        val crf = (38 - settings.qualitySlider.coerceIn(0, 100) * 0.18).toInt().coerceIn(22, 38)
        val audioBps = when {
            settings.removeAudio -> 0
            settings.audioBitrateKbps != null -> settings.audioBitrateKbps * 1000
            else -> when (strength) {
                CompressionStrength.FAST, CompressionStrength.BALANCED -> 128_000
                else -> 96_000
            }
        }

        val maxEdge = videoMeta?.let { VideoMetadataProbe.maxLongEdge(it, settings.resolution) }
        val targetFps = videoMeta?.let { VideoMetadataProbe.targetFps(it, settings.frameRate) }
        val (tw, th) = videoMeta?.let { VideoMetadataProbe.targetDimensions(it, settings.resolution) } ?: (null to null)

        return CompressionProfile(
            mode = mode,
            strength = strength,
            videoBitrateFactor = bitrateFactor,
            audioBitrateBps = if (settings.removeAudio) 0 else audioBps.coerceAtLeast(32_000),
            maxVideoLongEdge = maxEdge,
            photoWebpQuality = photoQuality(settings),
            ffmpegCrf = crf,
            ffmpegPreset = if (strength == CompressionStrength.SMALLEST) "slow" else "medium",
            videoCodec = settings.videoCodec,
            removeAudio = settings.removeAudio,
            volumePercent = settings.volumePercent.coerceIn(0, 100),
            targetFps = targetFps,
            targetWidth = tw,
            targetHeight = th,
            preferFfmpeg = useFfmpeg,
        )
    }

    private fun preferFfmpeg(settings: CompressJobSettings): Boolean =
        settings.qualitySlider < 15 || settings.platformTarget != null ||
            settings.frameRate != FrameRateChoice.ORIGINAL ||
            settings.volumePercent != 100 ||
            settings.videoCodec == VideoCodec.H264 && settings.qualitySlider < 40

    private fun photoQuality(settings: CompressJobSettings): Int {
        val base = 100 - settings.qualitySlider.coerceIn(0, 100) * 0.65
        return base.toInt().coerceIn(28, 92)
    }

    fun summaryLabel(settings: CompressJobSettings): String {
        val tier = settings.presetTier.name.lowercase().replaceFirstChar { it.titlecase() }
        val platform = settings.platformTarget?.label?.let { " · $it" } ?: ""
        return "Adaptive · $tier$platform"
    }
}
