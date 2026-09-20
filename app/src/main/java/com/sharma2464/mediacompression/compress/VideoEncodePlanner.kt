package com.sharma2464.mediacompression.compress

import androidx.media3.common.MimeTypes
import java.io.File

/**
 * Target-size and bitrate planning adapted from Josh Atticus Compressor (MIT).
 * https://github.com/JoshAtticus/Compressor
 */
data class PlannedVideoEncode(
    val videoMime: String,
    val targetWidth: Int?,
    val targetHeight: Int?,
    val outputFps: Int?,
    val videoBitrateBps: Int,
    val audioBitrateBps: Int,
)

object VideoEncodePlanner {
    fun planWithDuration(
        file: File,
        settings: CompressJobSettings,
        videoMeta: VideoMetadata,
        targetBytes: Long?,
    ): PlannedVideoEncode {
        val probe = if (file.isFile) VideoTrackProbe.probe(file) else MediaTrackProbe(null, null)
        val durationMs = videoMeta.durationMs.coerceAtLeast(1L)
        val origH = videoMeta.height
        val origW = videoMeta.width
        val origFps = (videoMeta.frameRate ?: 30f).toInt().coerceAtLeast(1)

        val (baseW, baseH) = VideoMetadataProbe.targetDimensions(videoMeta, settings.resolution)
        var targetW = baseW
        var targetH = baseH
        var outputFps = VideoMetadataProbe.targetFps(videoMeta, settings.frameRate)

        var audioBps = when {
            settings.removeAudio -> 0
            settings.audioBitrateKbps != null -> settings.audioBitrateKbps * 1000
            else -> 128_000
        }

        val targetMb = targetBytes?.let { it / (1024f * 1024f) }
        if (targetMb != null && targetMb > 0f) {
            val adjusted = autoAdjustForTargetMb(
                originalHeight = origH,
                originalWidth = origW,
                originalFps = origFps,
                videoCodec = settings.videoCodec,
                removeAudio = settings.removeAudio,
                durationMs = durationMs,
                targetMb = targetMb,
                startHeight = targetH,
                startFps = outputFps ?: origFps,
                startAudioBps = audioBps,
            )
            targetH = adjusted.outputHeight
            targetW = scaleWidth(origW, origH, targetH)
            outputFps = adjusted.outputFps
            audioBps = adjusted.audioBitrateBps
        }

        val mime = when (settings.videoCodec) {
            VideoCodec.H265 -> MimeTypes.VIDEO_H265
            VideoCodec.H264 -> MimeTypes.VIDEO_H264
        }
        val durationSec = durationMs / 1000.0
        val effectiveFps = outputFps ?: origFps
        val videoBr = targetBitrateFromSize(
            targetMb = targetMb ?: 10f,
            durationSec = durationSec,
            audioBps = audioBps,
            removeAudio = settings.removeAudio,
            outputHeight = targetH,
            outputFps = effectiveFps,
            videoMime = mime,
            originalBitrate = videoMeta.bitrateBps ?: probe.video?.bitrate ?: 0,
        )

        val tw = if (targetW < origW || targetH < origH) targetW else null
        val th = if (targetW < origW || targetH < origH) targetH else null

        return PlannedVideoEncode(mime, tw, th, outputFps, videoBr, audioBps)
    }

    private fun scaleWidth(origW: Int, origH: Int, targetH: Int): Int {
        if (origH <= 0) return origW
        return (origW.toLong() * targetH / origH).toInt().coerceAtLeast(2)
    }

    private data class AdjustResult(
        val outputHeight: Int,
        val outputFps: Int,
        val audioBitrateBps: Int,
    )

    private fun autoAdjustForTargetMb(
        originalHeight: Int,
        originalWidth: Int,
        originalFps: Int,
        videoCodec: VideoCodec,
        removeAudio: Boolean,
        durationMs: Long,
        targetMb: Float,
        startHeight: Int,
        startFps: Int,
        startAudioBps: Int,
    ): AdjustResult {
        var h = startHeight.coerceAtMost(originalHeight)
        var fps = startFps
        var audio = startAudioBps
        val isVertical = originalHeight > originalWidth

        fun minMb(height: Int, fpsVal: Int, audioBps: Int): Float {
            val minBr = minVideoBitrateBps(height, fpsVal, videoCodec)
            val sec = durationMs / 1000f
            if (sec <= 0f) return targetMb
            val audioBits = if (removeAudio) 0f else audioBps.toFloat() * sec
            return ((minBr * sec + audioBits) / 8f) / (1024f * 1024f)
        }

        var attempts = 0
        while (minMb(h, fps, audio) > targetMb && attempts++ < 20) {
            if (!removeAudio && audio > 128_000) {
                audio = 128_000
                continue
            }
            if (fps > 30) {
                fps = 30
                continue
            }
            val shortSide = if (isVertical && originalWidth > 0) h * originalWidth / originalHeight else h
            val newShort = when {
                shortSide > 2160 -> 2160
                shortSide > 1080 -> 1080
                shortSide > 720 -> 720
                shortSide > 480 -> 480
                else -> 360
            }
            val newH = if (isVertical && originalWidth > 0) {
                ((newShort.toLong() * originalHeight + originalWidth - 1) / originalWidth).toInt()
            } else {
                newShort
            }
            if (newH < h) {
                h = newH
                continue
            }
            if (fps > 24) {
                fps = 24
                continue
            }
            break
        }
        return AdjustResult(h.coerceAtLeast(2), fps, audio)
    }

    private fun minVideoBitrateBps(height: Int, fps: Int, codec: VideoCodec): Long {
        var base = when {
            height >= 2160 -> 4_000_000L
            height >= 1440 -> 2_500_000L
            height >= 1080 -> 1_500_000L
            height >= 720 -> 1_000_000L
            height >= 480 -> 500_000L
            else -> 350_000L
        }
        if (codec == VideoCodec.H265) base = (base * 0.7).toLong()
        if (fps > 45) base = (base * 1.5).toLong()
        return base
    }

    private fun targetBitrateFromSize(
        targetMb: Float,
        durationSec: Double,
        audioBps: Int,
        removeAudio: Boolean,
        outputHeight: Int,
        outputFps: Int,
        videoMime: String,
        originalBitrate: Int,
    ): Int {
        if (durationSec <= 0) return 2_000_000
        val targetBits = targetMb * 8 * 1024 * 1024
        val audioBits = if (removeAudio) 0.0 else audioBps.toDouble() * durationSec
        val overheadBits = targetBits * 0.02 + 50 * 1024 * 8
        val videoBits = (targetBits - audioBits - overheadBits).coerceAtLeast(targetBits * 0.1)
        val calculated = (videoBits / durationSec).toLong()
        val codec = if (videoMime == MimeTypes.VIDEO_H265) VideoCodec.H265 else VideoCodec.H264
        val minBr = minVideoBitrateBps(outputHeight, outputFps, codec)
        val cap = if (originalBitrate > 0) originalBitrate.toLong() else Long.MAX_VALUE
        return calculated.coerceIn(minBr, cap).toInt()
    }
}
