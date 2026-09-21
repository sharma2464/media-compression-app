package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.compress.VideoCodecMime.labelForMime
import com.sharma2464.mediacompression.settings.VideoEngine
import java.util.Locale

object CompressJobSummary {
    fun engineLabel(engine: VideoEngine): String = when (engine) {
        VideoEngine.MEDIA3 -> "Media3 (target size)"
        VideoEngine.LIGHT_COMPRESSOR -> "LightCompressor"
    }

    fun lines(
        settings: CompressJobSettings,
        modeLabel: String,
        videoMeta: VideoMetadata? = null,
        videoEngine: VideoEngine? = null,
    ): List<String> {
        val out = mutableListOf<String>()
        out += "Mode: $modeLabel"
        videoEngine?.let { out += "Encoder: ${engineLabel(it)}" }
        out += "Target size: ${formatMb(settings.targetSizeMb)}"
        settings.platformTarget?.let { out += "Platform cap: ${it.label}" }
        out += "Video codec: ${labelForMime(settings.effectiveVideoMime())}"
        out += "Resolution: ${resolutionLabel(settings.resolution, videoMeta)}"
        out += "Framerate: ${frameRateLabel(settings.frameRate, videoMeta)}"
        out += "Quality preset: ${settings.presetTier.name.lowercase().replaceFirstChar { it.titlecase() }}"
        if (settings.removeAudio) {
            out += "Audio: removed"
        } else {
            val ab = settings.audioBitrateKbps?.let { "${it} kbps" } ?: "original bitrate"
            out += "Audio: $ab · volume ${settings.volumePercent}%"
        }
        return out
    }

    fun progressLines(
        currentFileName: String,
        filePercent: Int,
        batchPercent: Int,
        speedLabel: String,
        phaseLabel: String?,
    ): List<String> = buildList {
        add("Current file: $currentFileName")
        add("This file: $filePercent%")
        add("Batch overall: $batchPercent%")
        add("Speed: $speedLabel")
        if (!phaseLabel.isNullOrBlank()) {
            add("Phase: $phaseLabel")
        }
    }

    private fun formatMb(mb: Float): String =
        String.format(Locale.US, "%.1f MB", mb.coerceAtLeast(0.1f))

    private fun resolutionLabel(choice: ResolutionChoice, meta: VideoMetadata?): String {
        if (choice == ResolutionChoice.ORIGINAL && meta != null) {
            return "Original (${meta.width}×${meta.height})"
        }
        return when (choice) {
            ResolutionChoice.ORIGINAL -> "Original"
            ResolutionChoice.P1080 -> "1080p max"
            ResolutionChoice.THREE_QUARTERS -> "¾ size"
            ResolutionChoice.P720 -> "720p max"
            ResolutionChoice.P540 -> "540p max"
            ResolutionChoice.P480 -> "480p max"
            ResolutionChoice.QUARTER -> "¼ size"
        }
    }

    private fun frameRateLabel(choice: FrameRateChoice, meta: VideoMetadata?): String {
        if (choice == FrameRateChoice.ORIGINAL && meta?.frameRate != null) {
            return "Original (${meta.frameRate.toInt()} fps)"
        }
        return when (choice) {
            FrameRateChoice.ORIGINAL -> "Original"
            FrameRateChoice.FPS_60 -> "60 fps"
            FrameRateChoice.FPS_30 -> "30 fps"
            FrameRateChoice.FPS_24 -> "24 fps"
            FrameRateChoice.FPS_15 -> "15 fps"
            FrameRateChoice.FPS_10 -> "10 fps"
        }
    }
}
