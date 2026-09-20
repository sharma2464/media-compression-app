package com.sharma2464.mediacompression.compress

import org.json.JSONObject

enum class PresetTier { HIGH, MEDIUM, LOW }

enum class PlatformPreset(val label: String, val hint: String, val maxBytes: Long) {
    GITHUB("GitHub", "25 MB", 25L * 1024 * 1024),
    DISCORD("Discord", "8 MB", 8L * 1024 * 1024),
    EMAIL("Email", "10 MB", 10L * 1024 * 1024),
    STORIES("Stories", "30 MB", 30L * 1024 * 1024),
    MESSENGER("Messenger", "25 MB", 25L * 1024 * 1024),
    WHATSAPP("WhatsApp", "16 MB", 16L * 1024 * 1024),
    TELEGRAM("Telegram", "50 MB", 50L * 1024 * 1024),
}

enum class VideoCodec { H264, H265 }

enum class ResolutionChoice {
    ORIGINAL,
    P1080,
    THREE_QUARTERS,
    P720,
    P540,
    P480,
    QUARTER,
}

enum class FrameRateChoice {
    ORIGINAL,
    FPS_60,
    FPS_30,
    FPS_24,
    FPS_15,
}

data class CompressJobSettings(
    val qualitySlider: Int = 50,
    val presetTier: PresetTier = PresetTier.MEDIUM,
    val platformTarget: PlatformPreset? = null,
    val videoCodec: VideoCodec = VideoCodec.H265,
    val resolution: ResolutionChoice = ResolutionChoice.ORIGINAL,
    val frameRate: FrameRateChoice = FrameRateChoice.ORIGINAL,
    val removeAudio: Boolean = false,
    val audioBitrateKbps: Int? = null,
    val volumePercent: Int = 100,
) {
    fun toJson(): String = JSONObject().apply {
        put("qualitySlider", qualitySlider)
        put("presetTier", presetTier.name)
        put("platformTarget", platformTarget?.name)
        put("videoCodec", videoCodec.name)
        put("resolution", resolution.name)
        put("frameRate", frameRate.name)
        put("removeAudio", removeAudio)
        if (audioBitrateKbps != null) put("audioBitrateKbps", audioBitrateKbps)
        put("volumePercent", volumePercent)
    }.toString()

    companion object {
        val DEFAULT = CompressJobSettings()

        fun fromJson(raw: String?): CompressJobSettings? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                CompressJobSettings(
                    qualitySlider = o.optInt("qualitySlider", 50),
                    presetTier = PresetTier.valueOf(o.optString("presetTier", PresetTier.MEDIUM.name)),
                    platformTarget = o.optString("platformTarget", "").takeIf { it.isNotEmpty() }
                        ?.let { PlatformPreset.valueOf(it) },
                    videoCodec = VideoCodec.valueOf(o.optString("videoCodec", VideoCodec.H265.name)),
                    resolution = ResolutionChoice.valueOf(o.optString("resolution", ResolutionChoice.ORIGINAL.name)),
                    frameRate = FrameRateChoice.valueOf(o.optString("frameRate", FrameRateChoice.ORIGINAL.name)),
                    removeAudio = o.optBoolean("removeAudio", false),
                    audioBitrateKbps = if (o.has("audioBitrateKbps")) o.getInt("audioBitrateKbps") else null,
                    volumePercent = o.optInt("volumePercent", 100),
                )
            }.getOrNull()
        }
    }
}
