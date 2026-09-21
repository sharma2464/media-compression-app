package com.sharma2464.mediacompression.compress

import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.roundToInt

data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bitrateBps: Int?,
    val frameRate: Float?,
)

object VideoMetadataProbe {
    fun probeUri(uriString: String?): VideoMetadata? {
        if (uriString.isNullOrBlank()) return null
        return if (uriString.startsWith("file://")) {
            val path = uriString.removePrefix("file://")
            probe(File(path))
        } else {
            null
        }
    }

    fun probe(file: File): VideoMetadata? {
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w == null || h == null) return null
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                ?: parseFraction(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT), duration)
            VideoMetadata(w, h, duration, bitrate, fps)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun parseFraction(frameCount: String?, durationMs: Long): Float? {
        val frames = frameCount?.toLongOrNull() ?: return null
        if (durationMs <= 0) return null
        return (frames * 1000f / durationMs).coerceIn(1f, 240f)
    }

    fun targetDimensions(meta: VideoMetadata, choice: ResolutionChoice): Pair<Int, Int> {
        val w = meta.width
        val h = meta.height
        val longEdge = maxOf(w, h)
        val cap = when (choice) {
            ResolutionChoice.ORIGINAL -> longEdge
            ResolutionChoice.P1080 -> 1080
            ResolutionChoice.THREE_QUARTERS -> (longEdge * 0.75).roundToInt()
            ResolutionChoice.P720 -> 720
            ResolutionChoice.P540 -> 540
            ResolutionChoice.P480 -> 480
            ResolutionChoice.QUARTER -> (longEdge * 0.25).roundToInt().coerceAtLeast(144)
        }
        if (cap >= longEdge) return w to h
        val scale = cap.toFloat() / longEdge
        return (w * scale).roundToInt().coerceAtLeast(2) to (h * scale).roundToInt().coerceAtLeast(2)
    }

    fun targetFps(meta: VideoMetadata, choice: FrameRateChoice): Int? = when (choice) {
        FrameRateChoice.ORIGINAL -> null
        FrameRateChoice.FPS_60 -> if ((meta.frameRate ?: 0f) >= 55f) 60 else 30
        FrameRateChoice.FPS_30 -> 30
        FrameRateChoice.FPS_24 -> 24
        FrameRateChoice.FPS_15 -> 15
        FrameRateChoice.FPS_10 -> 10
    }

    fun maxLongEdge(meta: VideoMetadata, choice: ResolutionChoice): Int? {
        if (choice == ResolutionChoice.ORIGINAL) return null
        val (tw, th) = targetDimensions(meta, choice)
        return maxOf(tw, th)
    }
}
