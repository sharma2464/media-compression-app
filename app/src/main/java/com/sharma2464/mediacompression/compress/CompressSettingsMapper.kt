package com.sharma2464.mediacompression.compress

import androidx.media3.common.MimeTypes
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
        var outputVideoHeight: Int? = null
        var plannedFps: Int? = null
        var plannedAudioBps: Int? = null
        var videoCodec = VideoCodecMime.codecFromMime(settings.effectiveVideoMime())
        val outputMime = settings.effectiveVideoMime()

        val targetBytes = (settings.targetSizeMb * 1024 * 1024).toLong()
            .takeIf { settings.targetSizeMb > 0f }
        if (targetBytes != null && videoMeta != null && videoMeta.durationMs > 0 && videoFile != null) {
            val planned = VideoEncodePlanner.planWithDuration(
                videoFile,
                settings,
                videoMeta,
                targetBytes,
            )
            targetVideoBitrateBps = planned.videoBitrateBps
            outputVideoHeight = planned.outputVideoHeight.takeIf { it > 0 }
            plannedFps = planned.outputFps
            plannedAudioBps = planned.audioBitrateBps
            videoCodec = if (planned.videoMime == androidx.media3.common.MimeTypes.VIDEO_H264) {
                VideoCodec.H264
            } else {
                VideoCodec.H265
            }
            val sourceBr = videoMeta.bitrateBps?.toDouble() ?: 4_000_000.0
            bitrateFactor = minOf(bitrateFactor, planned.videoBitrateBps / sourceBr)
        } else if (videoMeta != null && settings.platformTarget != null && videoMeta.durationMs > 0) {
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

        val photoPlan = if (videoMeta == null && videoFile != null && settings.targetSizeMb > 0f) {
            PhotoEncodePlanner.plan(videoFile, settings)
        } else {
            null
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

        val maxEdge = if (outputVideoHeight == null) {
            videoMeta?.let { VideoMetadataProbe.maxLongEdge(it, resolution) }
        } else {
            null
        }
        val targetFps = plannedFps?.takeIf { it > 0 }
            ?: videoMeta?.let { VideoMetadataProbe.targetFps(it, frameRate) }

        val (tw, th) = if (outputVideoHeight != null && videoMeta != null) {
            VideoDimensions.presentationSize(videoMeta.width, videoMeta.height, outputVideoHeight)
                ?: (null to null)
        } else {
            videoMeta?.let { VideoMetadataProbe.targetDimensions(it, resolution) } ?: (null to null)
        }

        val (preferAudioPassthrough, audioMime) = when (settings.audioFormat) {
            AudioFormatChoice.ORIGINAL_PASSTHROUGH -> true to null
            AudioFormatChoice.AAC -> false to MimeTypes.AUDIO_AAC
            AudioFormatChoice.OPUS -> false to MimeTypes.AUDIO_OPUS
        }

        return CompressionProfile(
            mode = mode,
            strength = strength,
            videoBitrateFactor = bitrateFactor,
            audioBitrateBps = if (settings.removeAudio) 0 else audioBps.coerceAtLeast(32_000),
            maxVideoLongEdge = maxEdge,
            photoWebpQuality = photoPlan?.webpQuality ?: photoQuality(settings),
            maxPhotoLongEdge = photoPlan?.maxLongEdge,
            videoCodec = videoCodec,
            removeAudio = settings.removeAudio,
            volumePercent = settings.volumePercent.coerceIn(0, 100),
            targetFps = targetFps,
            targetWidth = tw,
            targetHeight = th,
            outputVideoHeight = outputVideoHeight,
            preferFfmpeg = false,
            targetVideoBitrateBps = targetVideoBitrateBps,
            videoMime = outputMime,
            audioMime = audioMime,
            preferAudioPassthrough = preferAudioPassthrough,
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
