package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.compress.VideoCodecMime.codecFromMime
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.DefaultAudioConfig
import com.sharma2464.mediacompression.settings.DefaultVideoConfig
import kotlin.math.min

object CompressDefaults {
    fun initialJobSettings(
        settings: AppSettings,
        targetSizeMb: Float,
        videoMeta: VideoMetadata?,
    ): CompressJobSettings {
        val video = settings.defaultVideoConfig
        val audio = settings.defaultAudioConfig
        var job = CompressJobSettings.DEFAULT.copy(
            targetSizeMb = targetSizeMb,
            videoCodecMime = video.defaultCodecMime,
            videoCodec = codecFromMime(video.defaultCodecMime),
            removeAudio = audio.defaultRemoveAudio,
            volumePercent = audio.defaultVolumePercent.coerceIn(0, 200),
            audioBitrateKbps = audio.defaultAudioBitrate / 1000,
        )
        if (videoMeta != null) {
            job = job.copy(
                resolution = resolutionFromShortSide(video.defaultResolutionShortSide, videoMeta),
                frameRate = frameRateFromDefault(video.defaultFps),
            )
        }
        return job
    }

    private fun resolutionFromShortSide(shortSide: Int, meta: VideoMetadata): ResolutionChoice {
        if (shortSide <= 0) return ResolutionChoice.ORIGINAL
        val originalShort = min(meta.width, meta.height)
        if (shortSide >= originalShort) return ResolutionChoice.ORIGINAL
        return when {
            shortSide >= 1080 -> ResolutionChoice.P1080
            shortSide >= 720 -> ResolutionChoice.P720
            shortSide >= 480 -> ResolutionChoice.P480
            else -> ResolutionChoice.QUARTER
        }
    }

    private fun frameRateFromDefault(fps: Int): FrameRateChoice = when (fps) {
        60 -> FrameRateChoice.FPS_60
        30 -> FrameRateChoice.FPS_30
        24 -> FrameRateChoice.FPS_24
        else -> FrameRateChoice.ORIGINAL
    }
}
