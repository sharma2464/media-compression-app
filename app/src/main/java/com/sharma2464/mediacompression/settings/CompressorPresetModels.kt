package com.sharma2464.mediacompression.settings

import org.json.JSONArray
import org.json.JSONObject

/** Josh Atticus Compressor quality preset (MIT). */
data class QualityPresetConfig(
    val resolutionShortSide: Int = 0,
    val targetFps: Int = 0,
    val sizeRatio: Float = 0.7f,
    val audioBitrate: Int = 320_000,
    val label: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("resolutionShortSide", resolutionShortSide)
        put("targetFps", targetFps)
        put("sizeRatio", sizeRatio.toDouble())
        put("audioBitrate", audioBitrate)
        if (label != null) put("label", label)
    }

    companion object {
        fun fromJson(raw: String?): QualityPresetConfig {
            if (raw.isNullOrBlank()) return QualityPresetConfig()
            return runCatching {
                val o = JSONObject(raw)
                QualityPresetConfig(
                    resolutionShortSide = o.optInt("resolutionShortSide", 0),
                    targetFps = o.optInt("targetFps", 0),
                    sizeRatio = o.optDouble("sizeRatio", 0.7).toFloat(),
                    audioBitrate = o.optInt("audioBitrate", 320_000),
                    label = o.optString("label", "").takeIf { it.isNotEmpty() },
                )
            }.getOrDefault(QualityPresetConfig())
        }

        val defaultBest = QualityPresetConfig(0, 30, 0.15f, 160_000, "Best")
        val defaultHigh = QualityPresetConfig(0, 0, 0.7f, 320_000)
        val defaultMedium = QualityPresetConfig(1080, 30, 0.4f, 192_000)
        val defaultLow = QualityPresetConfig(720, 30, 0.2f, 128_000)
    }
}

data class TargetSizePreset(
    val id: String,
    val sizeMb: Float,
    val label: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sizeMb", sizeMb.toDouble())
        put("label", label)
    }

    companion object {
        fun fromJson(o: JSONObject) = TargetSizePreset(
            id = o.getString("id"),
            sizeMb = o.getDouble("sizeMb").toFloat(),
            label = o.getString("label"),
        )

        val defaults = listOf(
            TargetSizePreset("github", 10f, "GitHub"),
            TargetSizePreset("discord", 20f, "Discord"),
            TargetSizePreset("email", 25f, "Email"),
            TargetSizePreset("stories", 50f, "Stories • Nitro Basic"),
            TargetSizePreset("messenger", 100f, "Messenger • BlueSky"),
            TargetSizePreset("nitro", 500f, "Nitro • Reels"),
            TargetSizePreset("twitter", 512f, "Twitter/X"),
            TargetSizePreset("whatsapp", 2048f, "WhatsApp • Telegram"),
            TargetSizePreset("tg_premium", 4096f, "TG Premium • Feed"),
            TargetSizePreset("x_premium", 8192f, "X Premium"),
        )
    }
}

data class DefaultVideoConfig(
    val defaultCodecMime: String = "video/hevc",
    val defaultResolutionShortSide: Int = 0,
    val defaultFps: Int = 0,
    val defaultSizeRatio: Float = 0.7f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("defaultCodecMime", defaultCodecMime)
        put("defaultResolutionShortSide", defaultResolutionShortSide)
        put("defaultFps", defaultFps)
        put("defaultSizeRatio", defaultSizeRatio.toDouble())
    }

    companion object {
        fun fromJson(raw: String?): DefaultVideoConfig {
            if (raw.isNullOrBlank()) return DefaultVideoConfig()
            return runCatching {
                val o = JSONObject(raw)
                DefaultVideoConfig(
                    defaultCodecMime = o.optString("defaultCodecMime", "video/hevc"),
                    defaultResolutionShortSide = o.optInt("defaultResolutionShortSide", 0),
                    defaultFps = o.optInt("defaultFps", 0),
                    defaultSizeRatio = o.optDouble("defaultSizeRatio", 0.7).toFloat(),
                )
            }.getOrDefault(DefaultVideoConfig())
        }
    }
}

data class DefaultAudioConfig(
    val defaultAudioBitrate: Int = 192_000,
    val defaultRemoveAudio: Boolean = false,
    val defaultVolumePercent: Int = 100,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("defaultAudioBitrate", defaultAudioBitrate)
        put("defaultRemoveAudio", defaultRemoveAudio)
        put("defaultVolumePercent", defaultVolumePercent)
    }

    companion object {
        fun fromJson(raw: String?): DefaultAudioConfig {
            if (raw.isNullOrBlank()) return DefaultAudioConfig()
            return runCatching {
                val o = JSONObject(raw)
                DefaultAudioConfig(
                    defaultAudioBitrate = o.optInt("defaultAudioBitrate", 192_000),
                    defaultRemoveAudio = o.optBoolean("defaultRemoveAudio", false),
                    defaultVolumePercent = o.optInt("defaultVolumePercent", 100),
                )
            }.getOrDefault(DefaultAudioConfig())
        }
    }
}

fun encodeTargetSizePresets(list: List<TargetSizePreset>): String =
    JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

fun decodeTargetSizePresets(raw: String?): List<TargetSizePreset> {
    if (raw.isNullOrBlank()) return TargetSizePreset.defaults
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                add(TargetSizePreset.fromJson(arr.getJSONObject(i)))
            }
        }
    }.getOrDefault(TargetSizePreset.defaults)
}
