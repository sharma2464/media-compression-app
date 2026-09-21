package com.sharma2464.mediacompression.settings

import androidx.media3.common.MimeTypes
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressionProfile
import com.sharma2464.mediacompression.compress.CompressionStrength
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.compress.PresetTier
import com.sharma2464.mediacompression.compress.VideoCodec
import com.sharma2464.mediacompression.compress.VideoCodecMime
import com.sharma2464.mediacompression.compress.effectiveVideoMime
import com.sharma2464.mediacompression.compress.VideoMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object FilenameBuilder {
    fun preview(
        segments: List<FilenameSegment>,
        sampleOriginalName: String = "Vacation_clip",
    ): String {
        return buildStem(
            segments = segments,
            originalDisplayName = "$sampleOriginalName.mp4",
            job = CompressJobSettings.DEFAULT.copy(presetTier = PresetTier.MEDIUM),
            profile = CompressionProfile.resolve(CompressionMode.ADAPTIVE, CompressionStrength.BALANCED),
            meta = VideoMetadata(1920, 1080, 60_000, 5_000_000, 30f),
        )
    }

    fun outputFileName(
        segments: List<FilenameSegment>,
        originalDisplayName: String,
        job: CompressJobSettings,
        profile: CompressionProfile,
        meta: VideoMetadata?,
    ): String {
        val stem = buildStem(segments, originalDisplayName, job, profile, meta)
        val ext = originalDisplayName.substringAfterLast('.', "mp4")
        return "$stem.$ext"
    }

    private fun buildStem(
        segments: List<FilenameSegment>,
        originalDisplayName: String,
        job: CompressJobSettings,
        profile: CompressionProfile,
        meta: VideoMetadata?,
    ): String {
        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val timeStr = SimpleDateFormat("HH-mm-ss", Locale.US).format(now)
        val randomStr = Random.nextInt(1000, 9999).toString()
        val resStr = when {
            profile.targetWidth != null && profile.targetHeight != null ->
                "${profile.targetWidth}x${profile.targetHeight}"
            profile.outputVideoHeight != null && profile.outputVideoHeight > 0 && meta != null -> {
                val w = meta.width * profile.outputVideoHeight / meta.height.coerceAtLeast(1)
                "${w}x${profile.outputVideoHeight}"
            }
            meta != null -> "${meta.width}x${meta.height}"
            else -> "Unknown"
        }
        val fpsStr = (profile.targetFps ?: meta?.frameRate?.toInt() ?: 30).toString()
        val videoBr = profile.targetVideoBitrateBps ?: 0
        val videoBitrateStr = if (videoBr >= 1_000_000) {
            String.format(Locale.US, "%.1fMbps", videoBr / 1_000_000f)
        } else {
            "${videoBr / 1000}kbps"
        }
        val audioBr = profile.audioBitrateBps
        val audioBitrateStr = "${audioBr / 1000}k"
        val mime = job.effectiveVideoMime()
        val encodingStr = when (mime) {
            MimeTypes.VIDEO_H265 -> "H265"
            MimeTypes.VIDEO_H264 -> "H264"
            MimeTypes.VIDEO_AV1 -> "AV1"
            MimeTypes.VIDEO_VP9 -> "VP9"
            MimeTypes.VIDEO_VP8 -> "VP8"
            else -> mime.substringAfterLast("/").uppercase(Locale.US)
        }
        val audioStatusStr = if (job.removeAudio) "NoAudio" else "WithAudio"
        val presetStr = when (job.presetTier) {
            PresetTier.HIGH -> "High"
            PresetTier.MEDIUM -> "Medium"
            PresetTier.LOW -> "Low"
        }
        val originalNameStr = originalDisplayName.substringBeforeLast(".").ifBlank { "Compressed" }

        val parts = mutableListOf<String>()
        for (segment in FilenameSegment.normalize(segments)) {
            val evaluated = when (segment) {
                is FilenameSegment.Text -> segment.value.trim()
                is FilenameSegment.Token -> when (segment.key) {
                    "original_name" -> originalNameStr
                    "compressed" -> "Compressed"
                    "date" -> dateStr
                    "time" -> timeStr
                    "random" -> randomStr
                    "resolution" -> resStr
                    "framerate", "fps" -> fpsStr
                    "bitrate" -> videoBitrateStr
                    "audio_bitrate" -> audioBitrateStr
                    "encoding", "codec" -> encodingStr
                    "audio_status" -> audioStatusStr
                    "preset" -> presetStr
                    else -> segment.key
                }
            }
            if (evaluated.isNotBlank()) parts.add(evaluated)
        }
        val raw = parts.joinToString("_")
        val sanitized = raw
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (sanitized.isBlank()) "Compressed_${System.currentTimeMillis()}" else sanitized
    }
}
