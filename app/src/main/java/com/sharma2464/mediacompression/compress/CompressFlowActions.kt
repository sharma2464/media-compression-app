package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.compress.VideoCodecMime.codecFromMime
import com.sharma2464.mediacompression.compress.VideoCodec
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.QualityPresetConfig
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

class CompressFlowActions(
    private val appSettings: AppSettings,
    private val originalSizeBytes: Long,
    private val current: () -> CompressJobSettings,
    private val onChange: (CompressJobSettings) -> Unit,
    private val meta: VideoMetadata?,
    private val videoFile: File? = null,
) {
    private val originalMb: Float
        get() = originalSizeBytes / (1024f * 1024f).coerceAtLeast(0.01f)

    fun applyPreset(tier: PresetTier) {
        val config = appSettings.qualityPresetFor(tier)
        val targetMb = (originalMb * config.sizeRatio).coerceAtLeast(0.1f)
        val slider = when (tier) {
            PresetTier.BEST -> 65
            PresetTier.HIGH -> 75
            PresetTier.MEDIUM -> 50
            PresetTier.LOW -> 25
        }
        var next = current().copy(
            presetTier = tier,
            qualitySlider = slider,
            targetSizeMb = targetMb,
            platformTarget = null,
            removeAudio = false,
            audioBitrateKbps = if (config.audioBitrate > 0) config.audioBitrate / 1000 else null,
        )
        if (tier == PresetTier.BEST) {
            val mime = VideoCodecMime.pickEfficientMime(appSettings.allCodecsEnabled)
            next = next.copy(
                videoCodecMime = mime,
                videoCodec = codecFromMime(mime),
                audioFormat = AudioFormatChoice.AAC,
                resolution = ResolutionChoice.ORIGINAL,
            )
        }
        if (meta != null && config.resolutionShortSide > 0) {
            val m = meta
            val originalShort = min(m.width, m.height)
            if (config.resolutionShortSide < originalShort) {
                next = next.copy(
                    resolution = when {
                        config.resolutionShortSide >= 1080 -> ResolutionChoice.P1080
                        config.resolutionShortSide >= 720 -> ResolutionChoice.P720
                        config.resolutionShortSide >= 480 -> ResolutionChoice.P480
                        else -> ResolutionChoice.QUARTER
                    },
                )
            }
        }
        if (meta != null && config.targetFps > 0) {
            next = next.copy(
                frameRate = when (config.targetFps) {
                    60 -> FrameRateChoice.FPS_60
                    30 -> FrameRateChoice.FPS_30
                    24 -> FrameRateChoice.FPS_24
                    else -> FrameRateChoice.ORIGINAL
                },
            )
        }
        onChange(autoAdjustForTarget(next))
    }

    fun setTargetSize(mb: Float) {
        val next = autoAdjustForTarget(
            current().copy(targetSizeMb = mb.coerceAtLeast(0.1f), platformTarget = null),
        )
        onChange(next)
    }

    fun setTargetSizePreview(mb: Float) {
        onChange(current().copy(targetSizeMb = mb.coerceAtLeast(0.1f)))
    }

    fun setVideoCodec(mime: String) {
        onChange(
            current().copy(
                videoCodecMime = mime,
                videoCodec = VideoCodecMime.codecFromMime(mime),
            ),
        )
    }

    fun setResolution(shortSide: Int) {
        val m = meta ?: return
        val originalShort = min(m.width, m.height)
        if (shortSide >= originalShort) {
            onChange(current().copy(resolution = ResolutionChoice.ORIGINAL))
            return
        }
        val choice = when {
            shortSide >= 1080 -> ResolutionChoice.P1080
            shortSide >= 720 -> ResolutionChoice.P720
            shortSide >= 540 -> ResolutionChoice.P540
            shortSide >= 480 -> ResolutionChoice.P480
            shortSide >= (originalShort * 0.75).roundToInt() -> ResolutionChoice.THREE_QUARTERS
            else -> ResolutionChoice.QUARTER
        }
        onChange(current().copy(resolution = choice))
    }

    fun setFps(fps: Int) {
        val choice = when (fps) {
            0 -> FrameRateChoice.ORIGINAL
            60 -> FrameRateChoice.FPS_60
            30 -> FrameRateChoice.FPS_30
            24 -> FrameRateChoice.FPS_24
            15 -> FrameRateChoice.FPS_15
            10 -> FrameRateChoice.FPS_10
            else -> FrameRateChoice.ORIGINAL
        }
        onChange(current().copy(frameRate = choice))
    }

    fun toggleRemoveAudio() {
        onChange(current().copy(removeAudio = !current().removeAudio))
    }

    fun setAudioBitrate(bps: Int) {
        onChange(
            current().copy(
                audioBitrateKbps = if (bps <= 0) null else bps / 1000,
            ),
        )
    }

    fun setAudioVolume(volume: Float) {
        onChange(current().copy(volumePercent = (volume * 100f).roundToInt().coerceIn(0, 200)))
    }

    fun setAudioFormat(format: AudioFormatChoice) {
        onChange(current().copy(audioFormat = format))
    }

    fun acceptAllSuggestions(ui: CompressFlowUiState, file: java.io.File?) {
        val m = meta ?: return
        if (file == null) return
        onChange(ui.suggestedSettings(current(), m, file))
    }

    private fun autoAdjustForTarget(job: CompressJobSettings): CompressJobSettings {
        val m = meta ?: return job
        val file = videoFile ?: return job
        if (m.durationMs <= 0 || job.targetSizeMb <= 0f) return job
        val bytes = (job.targetSizeMb * 1024 * 1024).toLong()
        val planned = VideoEncodePlanner.planWithDuration(file, job, m, bytes)
        var next = job
        val plannedFps = planned.outputFps
        if (plannedFps != null && plannedFps > 0) {
            next = next.copy(
                frameRate = when (plannedFps) {
                    60 -> FrameRateChoice.FPS_60
                    30 -> FrameRateChoice.FPS_30
                    24 -> FrameRateChoice.FPS_24
                    15 -> FrameRateChoice.FPS_15
                    10 -> FrameRateChoice.FPS_10
                    else -> FrameRateChoice.ORIGINAL
                },
            )
        }
        if (planned.outputVideoHeight > 0) {
            next = next.copy(resolution = resolutionChoiceFor(m, planned.outputVideoHeight))
        }
        if (!job.removeAudio && planned.audioBitrateBps > 0) {
            next = next.copy(audioBitrateKbps = planned.audioBitrateBps / 1000)
        }
        return next
    }

    private fun resolutionChoiceFor(meta: VideoMetadata, targetHeight: Int): ResolutionChoice {
        if (targetHeight <= 0 || targetHeight >= meta.height) return ResolutionChoice.ORIGINAL
        val longEdge = maxOf(meta.width, meta.height)
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
