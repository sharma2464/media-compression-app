package com.sharma2464.mediacompression.compress

/** Target-size chips from Josh Atticus Compressor (MIT). */
data class CompressTargetSizePreset(
    val id: String,
    val sizeMb: Float,
    val label: String,
)

val defaultCompressTargetSizePresets = listOf(
    CompressTargetSizePreset("github", 10f, "GitHub"),
    CompressTargetSizePreset("discord", 20f, "Discord"),
    CompressTargetSizePreset("email", 25f, "Email"),
    CompressTargetSizePreset("stories", 50f, "Stories • Nitro Basic"),
    CompressTargetSizePreset("messenger", 100f, "Messenger • BlueSky"),
    CompressTargetSizePreset("nitro", 500f, "Nitro • Reels"),
    CompressTargetSizePreset("twitter", 512f, "Twitter/X"),
    CompressTargetSizePreset("whatsapp", 2048f, "WhatsApp • Telegram"),
    CompressTargetSizePreset("tg_premium", 4096f, "TG Premium • Feed"),
    CompressTargetSizePreset("x_premium", 8192f, "X Premium"),
)

fun platformPresetForTargetMb(mb: Float): PlatformPreset? {
    val bytes = (mb * 1024 * 1024).toLong()
    return PlatformPreset.entries.minByOrNull { kotlin.math.abs(it.maxBytes - bytes) }
        ?.takeIf { kotlin.math.abs(it.maxBytes - bytes) < 2L * 1024 * 1024 }
}
