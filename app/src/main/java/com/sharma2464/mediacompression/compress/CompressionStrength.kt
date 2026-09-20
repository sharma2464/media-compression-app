package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode

enum class CompressionStrength {
    FAST,
    BALANCED,
    SMALL,
    SMALLEST,
    ;

    fun displayLabel(): String = when (this) {
        FAST -> "Fast"
        BALANCED -> "Balanced"
        SMALL -> "Smaller file"
        SMALLEST -> "Smallest file"
    }
}

data class CompressionProfile(
    val mode: CompressionMode,
    val strength: CompressionStrength,
    val videoBitrateFactor: Double = 0.30,
    val audioBitrateBps: Int = 128_000,
    val maxVideoLongEdge: Int? = null,
    val photoWebpQuality: Int = 55,
    val ffmpegCrf: Int = 28,
    val ffmpegPreset: String = "medium",
    val videoCodec: VideoCodec = VideoCodec.H265,
    val removeAudio: Boolean = false,
    val volumePercent: Int = 100,
    val targetFps: Int? = null,
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val preferFfmpeg: Boolean = false,
) {
    fun estimatedSizeRatio(kind: FileKind?): Double {
        if (mode == CompressionMode.LOSSLESS_ONLY) {
            return when (kind) {
                FileKind.VIDEO -> 0.95
                FileKind.PHOTO, FileKind.LIVE_PHOTO -> 0.98
                else -> 0.99
            }
        }
        val videoRatio = (videoBitrateFactor * 1.8).coerceIn(0.15, 0.85)
        return when (kind) {
            FileKind.VIDEO -> videoRatio
            FileKind.PHOTO, FileKind.LIVE_PHOTO -> (photoWebpQuality / 100.0).coerceIn(0.35, 0.95)
            FileKind.PDF -> 0.75
            FileKind.DOCUMENT -> 0.85
            FileKind.TEXT -> 0.5
            else -> 0.9
        }
    }

    fun batchModeLabel(): String {
        val modePart = when (mode) {
            CompressionMode.LOSSLESS_ONLY -> "Lossless only"
            CompressionMode.ADAPTIVE -> "Adaptive"
        }
        if (mode == CompressionMode.LOSSLESS_ONLY) return modePart
        return "$modePart · ${strength.displayLabel()}"
    }

    companion object {
        fun resolve(mode: CompressionMode, strength: CompressionStrength): CompressionProfile {
            val factor = when (strength) {
                CompressionStrength.FAST -> 0.45
                CompressionStrength.BALANCED -> 0.30
                CompressionStrength.SMALL, CompressionStrength.SMALLEST -> 0.18
            }
            val audio = when (strength) {
                CompressionStrength.FAST, CompressionStrength.BALANCED -> 128_000
                else -> 96_000
            }
            val edge = when (strength) {
                CompressionStrength.FAST -> 1080
                CompressionStrength.BALANCED -> null
                else -> 720
            }
            val photoQ = when (strength) {
                CompressionStrength.FAST -> 72
                CompressionStrength.BALANCED -> 55
                CompressionStrength.SMALL -> 40
                CompressionStrength.SMALLEST -> 32
            }
            return CompressionProfile(
                mode = mode,
                strength = strength,
                videoBitrateFactor = factor,
                audioBitrateBps = audio,
                maxVideoLongEdge = edge,
                photoWebpQuality = photoQ,
                ffmpegCrf = if (strength == CompressionStrength.SMALLEST) 32 else 28,
                ffmpegPreset = if (strength == CompressionStrength.SMALLEST) "slow" else "medium",
                preferFfmpeg = strength == CompressionStrength.SMALLEST,
            )
        }

        fun forSmallestFallback(mode: CompressionMode): CompressionProfile =
            resolve(mode, CompressionStrength.SMALL)
    }
}

object CompressionProgressPhases {
    const val STAGING_MAX = 10
    const val COMPRESS_MIN = 10
    const val COMPRESS_MAX = 90
    const val SAVING_MIN = 90

    fun stagingPercent(bytesCopied: Long, totalBytes: Long): Int {
        if (totalBytes <= 0) return STAGING_MAX
        val frac = (bytesCopied.toDouble() / totalBytes).coerceIn(0.0, 1.0)
        return (frac * STAGING_MAX).toInt().coerceIn(0, STAGING_MAX)
    }

    fun compressPercent(encoderPercent: Int): Int {
        val enc = encoderPercent.coerceIn(0, 100)
        return COMPRESS_MIN + enc * (COMPRESS_MAX - COMPRESS_MIN) / 100
    }

    fun savingPercent(bytesCopied: Long, totalBytes: Long): Int {
        if (totalBytes <= 0) return 100
        val frac = (bytesCopied.toDouble() / totalBytes).coerceIn(0.0, 1.0)
        return SAVING_MIN + (frac * (100 - SAVING_MIN)).toInt().coerceIn(SAVING_MIN, 100)
    }
}
