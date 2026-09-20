package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File

object CompressSettingsMapper {
    fun toProfile(
        mode: CompressionMode,
        settings: CompressJobSettings,
        videoMeta: VideoMetadata?,
        videoFile: File? = null,
    ): CompressionProfile {
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

        var resolution = settings.resolution
        var frameRate = settings.frameRate
        var targetVideoBitrateBps: Int? = null
        var plannedTw: Int? = null
        var plannedTh: Int? = null
        var plannedFps: Int? = null
        var plannedAudioBps: Int? = null
        val targetBytes = settings.platformTarget?.maxBytes
            ?: (settings.targetSizeMb * 1024 * 1024).toLong().takeIf { settings.targetSizeMb > 0f }
        targetBytes?.let { cap ->
            if (videoMeta != null && videoMeta.durationMs > 0 && videoFile != null) {
                val planned = VideoEncodePlanner.planWithDuration(
                    videoFile,
                    settings,
                    videoMeta,
                    cap,
                )
                targetVideoBitrateBps = planned.videoBitrateBps
                plannedTw = planned.targetWidth
                plannedTh = planned.targetHeight
                plannedFps = planned.outputFps
                plannedAudioBps = planned.audioBitrateBps
                val sourceBr = videoMeta.bitrateBps?.toDouble() ?: 4_000_000.0
                bitrateFactor = minOf(bitrateFactor, planned.videoBitrateBps / sourceBr)
            } else if (videoMeta != null && videoMeta.durationMs > 0 && settings.platformTarget != null) {
                val platform = settings.platformTarget!!
                val longEdge = maxOf(videoMeta.width, videoMeta.height)
                if (longEdge > 1080 && resolution == ResolutionChoice.ORIGINAL) {
                    resolution = ResolutionChoice.P1080
                }
                if (frameRate == FrameRateChoice.ORIGINAL && (videoMeta.frameRate ?: 0f) > 31f) {
                    frameRate = FrameRateChoice.FPS_30
                }
                val durationSec = videoMeta.durationMs / 1000.0
                val audioKbps = settings.audioBitrateKbps ?: 128
                val audioBits = if (settings.removeAudio) 0 else audioKbps * 1000
                val maxVideoBps = ((platform.maxBytes * 8) / durationSec - audioBits).coerceAtLeast(200_000.0)
                targetVideoBitrateBps = maxVideoBps.toInt()
                val sourceBr = videoMeta.bitrateBps?.toDouble() ?: 4_000_000.0
                bitrateFactor = minOf(bitrateFactor, maxVideoBps / sourceBr)
            }
        }

        val strength = when {
            bitrateFactor <= 0.2 -> CompressionStrength.SMALL
            bitrateFactor <= 0.35 -> CompressionStrength.BALANCED
            else -> CompressionStrength.FAST
        }
        val audioBps = plannedAudioBps ?: when {
            settings.removeAudio -> 0
            settings.audioBitrateKbps != null -> settings.audioBitrateKbps * 1000
            else -> when (strength) {
                CompressionStrength.FAST, CompressionStrength.BALANCED -> 128_000
                else -> 96_000
            }
        }

        val maxEdge = if (plannedTw == null && plannedTh == null) {
            videoMeta?.let { VideoMetadataProbe.maxLongEdge(it, resolution) }
        } else {
            null
        }
        val targetFps = plannedFps?.takeIf { it > 0 } ?: videoMeta?.let { VideoMetadataProbe.targetFps(it, frameRate) }
        val (tw, th) = if (plannedTw != null && plannedTh != null) {
            plannedTw to plannedTh
        } else {
            videoMeta?.let { VideoMetadataProbe.targetDimensions(it, resolution) } ?: (null to null)
        }

        return CompressionProfile(
            mode = mode,
            strength = strength,
            videoBitrateFactor = bitrateFactor,
            audioBitrateBps = if (settings.removeAudio) 0 else audioBps.coerceAtLeast(32_000),
            maxVideoLongEdge = maxEdge,
            photoWebpQuality = photoQuality(settings),
            videoCodec = settings.videoCodec,
            removeAudio = settings.removeAudio,
            volumePercent = settings.volumePercent.coerceIn(0, 100),
            targetFps = targetFps,
            targetWidth = tw,
            targetHeight = th,
            preferFfmpeg = false,
            targetVideoBitrateBps = targetVideoBitrateBps,
        )
    }

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
