package com.sharma2464.mediacompression.compress

import android.text.format.Formatter
import androidx.media3.common.MimeTypes
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * UI state shaped like Josh Atticus Compressor for the compress flow (MIT).
 * https://github.com/JoshAtticus/Compressor
 */
data class CompressFlowUiState(
    val originalSize: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val originalBitrate: Int,
    val originalAudioBitrate: Int,
    val originalFps: Float,
    val durationMs: Long,
    val fileCount: Int,
    val batchLabel: String,
    val targetSizeMb: Float,
    val minimumSizeMb: Float,
    val estimatedSizeMb: Float,
    val targetResolutionHeight: Int,
    val targetFps: Int,
    val videoCodecMime: String,
    val supportedCodecs: List<String>,
    val removeAudio: Boolean,
    val audioBitrate: Int,
    val audioVolume: Float,
    val presetTier: PresetTier,
    val showBitrate: Boolean = true,
    val formattedOriginalSize: String,
    val formattedEstimatedSize: String,
    val targetBitrate: Int,
) {
    val targetSizeWarning: Boolean
        get() = durationMs > 0 && targetSizeMb < minimumSizeMb

    val estimatedSizeLabel: String
        get() = String.format(Locale.US, "%.1f MB", max(targetSizeMb, minimumSizeMb))

    fun suggestedSettings(current: CompressJobSettings, meta: VideoMetadata, videoFile: java.io.File?): CompressJobSettings {
        if (meta.durationMs <= 0 || videoFile == null) return current
        val bytes = (targetSizeMb * 1024 * 1024).toLong()
        val planned = VideoEncodePlanner.planWithDuration(videoFile, current, meta, bytes)
        var next = current
        val plannedFps = planned.outputFps
        if (plannedFps != null && plannedFps > 0) {
            next = next.copy(
                frameRate = when (plannedFps) {
                    60 -> FrameRateChoice.FPS_60
                    30 -> FrameRateChoice.FPS_30
                    24 -> FrameRateChoice.FPS_24
                    15 -> FrameRateChoice.FPS_15
                    else -> FrameRateChoice.ORIGINAL
                },
            )
        }
        if (planned.targetHeight != null && planned.targetWidth != null) {
            next = next.copy(resolution = resolutionChoiceFor(meta, planned.targetHeight))
        }
        if (!current.removeAudio && planned.audioBitrateBps > 0) {
            next = next.copy(audioBitrateKbps = planned.audioBitrateBps / 1000)
        }
        return next
    }

    companion object {
        fun build(
            context: android.content.Context,
            previewTotalBytes: Long,
            fileCount: Int,
            batchLabel: String,
            settings: CompressJobSettings,
            meta: VideoMetadata?,
            estimatedBytes: Long,
            supportedCodecs: List<String>,
        ): CompressFlowUiState {
            val w = meta?.width ?: 0
            val h = meta?.height ?: 0
            val fps = meta?.frameRate ?: 30f
            val duration = meta?.durationMs ?: 0L
            val origBr = meta?.bitrateBps ?: 0
            val (tw, th) = meta?.let { VideoMetadataProbe.targetDimensions(it, settings.resolution) } ?: (0 to 0)
            val targetH = if (th > 0 && th < h) th else 0
            val targetFps = meta?.let { VideoMetadataProbe.targetFps(it, settings.frameRate) } ?: 0
            val mime = when (settings.videoCodec) {
                VideoCodec.H265 -> MimeTypes.VIDEO_H265
                VideoCodec.H264 -> MimeTypes.VIDEO_H264
            }
            val audioBps = when {
                settings.removeAudio -> 0
                settings.audioBitrateKbps != null -> settings.audioBitrateKbps * 1000
                else -> 128_000
            }
            val minMb = minimumSizeMb(
                height = if (targetH > 0) targetH else h,
                fps = if (targetFps > 0) targetFps else fps.roundToInt(),
                codec = settings.videoCodec,
                durationMs = duration,
                removeAudio = settings.removeAudio,
                audioBps = audioBps,
            )
            val estMb = estimatedBytes / (1024f * 1024f)
            val targetMb = settings.targetSizeMb
            val videoBr = targetBitrateBps(targetMb, duration, audioBps, settings.removeAudio, targetH, targetFps, mime, origBr)

            return CompressFlowUiState(
                originalSize = previewTotalBytes,
                originalWidth = w,
                originalHeight = h,
                originalBitrate = origBr,
                originalAudioBitrate = 128_000,
                originalFps = fps,
                durationMs = duration,
                fileCount = fileCount,
                batchLabel = batchLabel,
                targetSizeMb = targetMb,
                minimumSizeMb = minMb,
                estimatedSizeMb = estMb,
                targetResolutionHeight = targetH,
                targetFps = targetFps ?: 0,
                videoCodecMime = mime,
                supportedCodecs = supportedCodecs,
                removeAudio = settings.removeAudio,
                audioBitrate = settings.audioBitrateKbps?.let { it * 1000 } ?: 0,
                audioVolume = settings.volumePercent / 100f,
                presetTier = settings.presetTier,
                formattedOriginalSize = Formatter.formatShortFileSize(context, previewTotalBytes),
                formattedEstimatedSize = Formatter.formatShortFileSize(context, estimatedBytes),
                targetBitrate = videoBr,
            )
        }

        private fun minimumSizeMb(
            height: Int,
            fps: Int,
            codec: VideoCodec,
            durationMs: Long,
            removeAudio: Boolean,
            audioBps: Int,
        ): Float {
            if (durationMs <= 0) return 0.1f
            val sec = durationMs / 1000f
            var base = when {
                height >= 2160 -> 4_000_000L
                height >= 1080 -> 1_500_000L
                height >= 720 -> 1_000_000L
                else -> 500_000L
            }
            if (codec == VideoCodec.H265) base = (base * 0.7).toLong()
            if (fps > 45) base = (base * 1.5).toLong()
            val audioBits = if (removeAudio) 0f else audioBps * sec
            return ((base * sec + audioBits) / 8f) / (1024f * 1024f)
        }

        private fun targetBitrateBps(
            targetMb: Float,
            durationMs: Long,
            audioBps: Int,
            removeAudio: Boolean,
            height: Int,
            fps: Int,
            mime: String,
            originalBitrate: Int,
        ): Int {
            val durationSec = durationMs / 1000.0
            if (durationSec <= 0) return 2_000_000
            val targetBits = targetMb * 8 * 1024 * 1024
            val audioBits = if (removeAudio) 0.0 else audioBps.toDouble() * durationSec
            val overheadBits = targetBits * 0.02 + 50 * 1024 * 8
            val videoBits = (targetBits - audioBits - overheadBits).coerceAtLeast(targetBits * 0.1)
            val calculated = (videoBits / durationSec).toLong()
            val codec = if (mime == MimeTypes.VIDEO_H265) VideoCodec.H265 else VideoCodec.H264
            val minBr = when {
                height >= 1080 -> 1_050_000L
                height >= 720 -> 700_000L
                else -> 350_000L
            }.let { if (codec == VideoCodec.H265) (it * 0.7).toLong() else it }
            val cap = if (originalBitrate > 0) originalBitrate.toLong() else Long.MAX_VALUE
            return calculated.coerceIn(minBr, cap).toInt()
        }

        private fun resolutionChoiceFor(meta: VideoMetadata, targetHeight: Int): ResolutionChoice {
            if (targetHeight <= 0 || targetHeight >= meta.height) return ResolutionChoice.ORIGINAL
            val longEdge = max(meta.width, meta.height)
            val newLong = (longEdge.toLong() * targetHeight / meta.height.coerceAtLeast(1)).toInt()
            return when {
                newLong >= longEdge -> ResolutionChoice.ORIGINAL
                newLong >= 1080 -> ResolutionChoice.P1080
                newLong >= 720 -> ResolutionChoice.P720
                newLong >= 540 -> ResolutionChoice.P540
                newLong >= 480 -> ResolutionChoice.P480
                else -> ResolutionChoice.QUARTER
            }
        }
    }
}
