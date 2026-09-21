package com.sharma2464.mediacompression.compress

import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.media3.common.MimeTypes
import kotlin.math.ceil

/**
 * Encoder capability checks from Josh Atticus Compressor (MIT).
 */
data class VideoCompressionPlan(
    val videoMime: String,
    val outputVideoHeight: Int,
    val outputFps: Int,
    val warnings: List<String> = emptyList(),
)

object VideoCompressionPlanner {
    fun build(
        probe: MediaTrackProbe,
        meta: VideoMetadata,
        settings: CompressJobSettings,
        plannedHeight: Int,
        plannedFps: Int?,
    ): VideoCompressionPlan {
        var mime = settings.effectiveVideoMime()
        var outputHeight = if (plannedHeight > 0) plannedHeight else 0
        var outputFps = plannedFps?.takeIf { it > 0 } ?: 0
        val warnings = mutableListOf<String>()

        val video = probe.video
        val sourceMime = video?.mimeType
        val sourceW = video?.width ?: meta.width
        val sourceH = video?.height ?: meta.height
        val sourceFps = video?.frameRate?.takeIf { it > 0f } ?: (meta.frameRate ?: 30f)

        if (!sourceMime.isNullOrBlank() && sourceW > 0 && sourceH > 0) {
            if (!isCodecSupported(sourceMime, sourceW, sourceH, sourceFps, encoder = false)) {
                warnings.add("Source video may be difficult to decode on this device.")
            }
        }

        if (!isOutputSupported(mime, meta.width, meta.height, outputHeight, outputFps)) {
            if (mime != MimeTypes.VIDEO_H264 && isOutputSupported(MimeTypes.VIDEO_H264, meta.width, meta.height, outputHeight, outputFps)) {
                mime = MimeTypes.VIDEO_H264
                warnings.add("Using H.264 for broader encoder support.")
            } else {
                val heights = listOf(1080, 720, 540, 480).filter { it in 2..meta.height }
                val fpsList = listOf(30, 24)
                var ok = false
                for (h in heights) {
                    for (fps in fpsList) {
                        if (isOutputSupported(MimeTypes.VIDEO_H264, meta.width, meta.height, h, fps)) {
                            mime = MimeTypes.VIDEO_H264
                            outputHeight = h
                            outputFps = fps
                            warnings.add("Reduced to ${h}p @ ${fps}fps for encoder support.")
                            ok = true
                            break
                        }
                    }
                    if (ok) break
                }
            }
        }

        return VideoCompressionPlan(mime, outputHeight, outputFps, warnings)
    }

    private fun isOutputSupported(mime: String, origW: Int, origH: Int, outputHeight: Int, fps: Int): Boolean {
        val h = if (outputHeight > 0) outputHeight else origH
        val f = if (fps > 0) fps else 30
        val size = VideoDimensions.presentationSize(origW, origH, h) ?: return isCodecSupported(mime, origW, origH, f.toFloat(), encoder = true)
        return isCodecSupported(mime, size.first, size.second, f.toFloat(), encoder = true)
    }

    private fun isCodecSupported(mime: String, width: Int, height: Int, fps: Float, encoder: Boolean): Boolean {
        return try {
            val safeFps = ceil(if (fps > 0f) fps.toDouble() else 30.0)
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos
                .asSequence()
                .filter { it.isEncoder == encoder }
                .filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
                .any { info ->
                    try {
                        val caps = info.getCapabilitiesForType(mime)
                        val videoCaps = caps.videoCapabilities ?: return@any false
                        videoCaps.areSizeAndRateSupported(width, height, safeFps) ||
                            videoCaps.areSizeAndRateSupported(height, width, safeFps)
                    } catch (_: Exception) {
                        false
                    }
                }
        } catch (_: Exception) {
            true
        }
    }
}
