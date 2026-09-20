package com.sharma2464.mediacompression.compress

import androidx.media3.common.MimeTypes
import java.io.File

/**
 * Target-size and bitrate planning adapted from Josh Atticus Compressor (MIT).
 * https://github.com/JoshAtticus/Compressor
 */
data class PlannedVideoEncode(
    val videoMime: String,
    /** Stored video height; 0 = keep original. */
    val outputVideoHeight: Int,
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
        val origW = videoMeta.width
        val origH = videoMeta.height
        val origFps = (videoMeta.frameRate ?: 30f).toInt().coerceAtLeast(1)

        var outputHeight = resolveOutputHeight(videoMeta, settings)
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
                startOutputHeight = outputHeight,
                startFps = outputFps ?: origFps,
                startAudioBps = audioBps,
            )
            outputHeight = adjusted.outputHeight
            outputFps = adjusted.outputFps
            audioBps = adjusted.audioBitrateBps
        }

        val compressionPlan = VideoCompressionPlanner.build(
            probe,
            videoMeta,
            settings,
            outputHeight,
            outputFps,
        )
        outputHeight = compressionPlan.outputVideoHeight
        if (compressionPlan.outputFps > 0) outputFps = compressionPlan.outputFps

        val mime = compressionPlan.videoMime
        val durationSec = durationMs / 1000.0
        val effectiveFps = outputFps ?: origFps
        val heightForBitrate = if (outputHeight > 0) outputHeight else origH
        val videoBr = targetBitrateFromSize(
            targetMb = targetMb ?: settings.targetSizeMb,
            durationSec = durationSec,
            audioBps = audioBps,
            removeAudio = settings.removeAudio,
            outputHeight = heightForBitrate,
            outputFps = effectiveFps,
            videoMime = mime,
            originalBitrate = videoMeta.bitrateBps ?: probe.video?.bitrate ?: 0,
        )

        return PlannedVideoEncode(mime, outputHeight, outputFps, videoBr, audioBps)
    }

    private fun resolveOutputHeight(meta: VideoMetadata, settings: CompressJobSettings): Int {
        if (settings.resolution == ResolutionChoice.ORIGINAL) return 0
        val longEdge = maxOf(meta.width, meta.height)
        val shortSideCap = when (settings.resolution) {
            ResolutionChoice.P1080 -> 1080
            ResolutionChoice.P720 -> 720
            ResolutionChoice.P540 -> 540
            ResolutionChoice.P480 -> 480
            ResolutionChoice.THREE_QUARTERS -> (longEdge * 0.75).toInt()
            ResolutionChoice.QUARTER -> (longEdge * 0.25).toInt().coerceAtLeast(144)
            ResolutionChoice.ORIGINAL -> return 0
        }
        return VideoDimensions.outputHeightForShortSide(meta.width, meta.height, shortSideCap)
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
        startOutputHeight: Int,
        startFps: Int,
        startAudioBps: Int,
    ): AdjustResult {
        var outputHeight = if (startOutputHeight > 0) startOutputHeight else originalHeight
        var fps = startFps
        var audio = startAudioBps

        fun minMb(height: Int, fpsVal: Int, audioBps: Int): Float {
            val minBr = minVideoBitrateBps(height, fpsVal, videoCodec)
            val sec = durationMs / 1000f
            if (sec <= 0f) return targetMb
            val audioBits = if (removeAudio) 0f else audioBps.toFloat() * sec
            return ((minBr * sec + audioBits) / 8f) / (1024f * 1024f)
        }

        var shortSide = VideoDimensions.shortSideForOutputHeight(originalWidth, originalHeight, outputHeight)
        var attempts = 0
        while (minMb(outputHeight, fps, audio) > targetMb && attempts++ < 24) {
            if (!removeAudio && audio > 128_000) {
                audio = 128_000
                continue
            }
            if (!removeAudio && audio > 96_000 && minMb(outputHeight, fps, audio) > targetMb * 1.5f) {
                audio = 96_000
                continue
            }
            if (fps > 30) {
                fps = 30
                continue
            }
            val newShort = when {
                shortSide > 2160 -> 2160
                shortSide > 1080 -> 1080
                shortSide > 720 -> 720
                shortSide > 480 -> 480
                shortSide > 360 -> 360
                shortSide > 240 -> 240
                else -> shortSide
            }
            if (newShort < shortSide) {
                shortSide = newShort
                outputHeight = VideoDimensions.outputHeightForShortSide(originalWidth, originalHeight, shortSide)
                continue
            }
            if (fps > 24) {
                fps = 24
                continue
            }
            break
        }
        return AdjustResult(outputHeight.coerceAtLeast(2), fps, audio)
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
        val sec = durationSec.toFloat()
        val minMb = if (sec > 0) {
            val audioBits = if (removeAudio) 0f else audioBps.toFloat() * sec
            ((minBr * sec + audioBits) / 8f) / (1024f * 1024f)
        } else {
            0f
        }
        val floor = if (targetMb > 0f && targetMb < minMb) 80_000L else minBr
        return calculated.coerceIn(floor, cap).toInt()
    }
}
