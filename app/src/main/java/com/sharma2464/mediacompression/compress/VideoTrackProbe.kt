package com.sharma2464.mediacompression.compress

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.MimeTypes
import java.io.File

/**
 * Track probe pattern from Josh Atticus Compressor (MIT).
 * https://github.com/JoshAtticus/Compressor
 */
data class VideoTrackInfo(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitrate: Int,
)

data class AudioTrackInfo(
    val mimeType: String,
    val bitrate: Int,
    val aacProfile: Int,
)

data class MediaTrackProbe(
    val video: VideoTrackInfo?,
    val audio: AudioTrackInfo?,
) {
    val hasAudio: Boolean get() = audio != null
}

object VideoTrackProbe {
    fun probe(file: File): MediaTrackProbe {
        val extractor = MediaExtractor()
        var video: VideoTrackInfo? = null
        var audio: AudioTrackInfo? = null
        try {
            extractor.setDataSource(file.path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    video == null && mime.startsWith("video/") -> {
                        val w = format.getInteger(MediaFormat.KEY_WIDTH)
                        val h = format.getInteger(MediaFormat.KEY_HEIGHT)
                        val br = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            format.getInteger(MediaFormat.KEY_BIT_RATE)
                        } else {
                            0
                        }
                        val fps = readFps(format)
                        video = VideoTrackInfo(mime, w, h, fps, br)
                    }
                    audio == null && mime.startsWith("audio/") -> {
                        val br = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            format.getInteger(MediaFormat.KEY_BIT_RATE)
                        } else {
                            0
                        }
                        val profile = if (format.containsKey("aac-profile")) {
                            format.getInteger("aac-profile")
                        } else {
                            -1
                        }
                        audio = AudioTrackInfo(mime, br, profile)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }
        return MediaTrackProbe(video, audio)
    }

    fun canPassthroughAudio(probe: MediaTrackProbe, removeAudio: Boolean, volumePercent: Int): Boolean {
        if (removeAudio || volumePercent != 100) return false
        val a = probe.audio ?: return false
        return a.mimeType == MimeTypes.AUDIO_AAC &&
            (a.aacProfile == -1 || a.aacProfile == android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC) &&
            a.bitrate > 0
    }

    private fun readFps(format: MediaFormat): Float {
        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) return 0f
        return try {
            format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
        } catch (_: Exception) {
            try {
                format.getFloat(MediaFormat.KEY_FRAME_RATE)
            } catch (_: Exception) {
                0f
            }
        }
    }
}
