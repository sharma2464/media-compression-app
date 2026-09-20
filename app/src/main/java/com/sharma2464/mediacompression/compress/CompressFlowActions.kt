package com.sharma2464.mediacompression.compress

import androidx.media3.common.MimeTypes
import kotlin.math.min
import kotlin.math.roundToInt

class CompressFlowActions(
    private val current: () -> CompressJobSettings,
    private val onChange: (CompressJobSettings) -> Unit,
    private val meta: VideoMetadata?,
) {
    fun applyPreset(tier: PresetTier) {
        val slider = when (tier) {
            PresetTier.HIGH -> 75
            PresetTier.MEDIUM -> 50
            PresetTier.LOW -> 25
        }
        onChange(
            current().copy(
                presetTier = tier,
                qualitySlider = slider,
                targetSizeMb = when (tier) {
                    PresetTier.HIGH -> 50f
                    PresetTier.MEDIUM -> 20f
                    PresetTier.LOW -> 10f
                },
            ),
        )
    }

    fun setTargetSize(mb: Float) {
        onChange(current().copy(targetSizeMb = mb, platformTarget = null))
    }

    fun setTargetSizePreview(mb: Float) {
        onChange(current().copy(targetSizeMb = mb))
    }

    fun setVideoCodec(mime: String) {
        val codec = when (mime) {
            MimeTypes.VIDEO_H264 -> VideoCodec.H264
            else -> VideoCodec.H265
        }
        onChange(current().copy(videoCodec = codec))
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

    fun acceptAllSuggestions(ui: CompressFlowUiState, file: java.io.File?) {
        val m = meta ?: return
        if (file == null) return
        onChange(ui.suggestedSettings(current(), m, file))
    }
}
