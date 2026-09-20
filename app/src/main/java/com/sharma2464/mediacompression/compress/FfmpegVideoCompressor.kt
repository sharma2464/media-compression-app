package com.sharma2464.mediacompression.compress

import android.media.MediaMetadataRetriever
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class FfmpegVideoCompressor {

    suspend fun compress(
        input: File,
        profile: CompressionProfile,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult = withContext(Dispatchers.IO) {
        val output = File(workDir, "${input.nameWithoutExtension}_ffmpeg.mp4")
        output.delete()
        val durationMs = readDurationMs(input).coerceAtLeast(1L)
        val vf = buildVideoFilter(input, profile)
        val codec = when (profile.videoCodec) {
            VideoCodec.H264 -> "libx264"
            VideoCodec.H265 -> "libx265"
        }
        val command = buildList {
            add("-y")
            add("-i")
            add(input.absolutePath)
            profile.targetFps?.let { fps ->
                add("-r")
                add(fps.toString())
            }
            if (vf != null) {
                add("-vf")
                add(vf)
            }
            add("-c:v")
            add(codec)
            add("-crf")
            add(profile.ffmpegCrf.toString())
            add("-preset")
            add(profile.ffmpegPreset)
            add("-threads")
            add("4")
            add("-map_metadata")
            add("0")
            add("-movflags")
            add("use_metadata_tags")
            if (profile.removeAudio) {
                add("-an")
            } else {
                add("-c:a")
                add("aac")
                add("-b:a")
                add("${profile.audioBitrateBps / 1000}k")
                if (profile.volumePercent != 100) {
                    add("-af")
                    add("volume=${profile.volumePercent / 100.0}")
                }
            }
            add(output.absolutePath)
        }.toTypedArray()

        suspendCancellableCoroutine { cont ->
            FFmpegKitConfig.enableStatisticsCallback { stats: Statistics ->
                val time = stats.time
                if (time > 0) {
                    val pct = ((time * 100) / durationMs).toInt().coerceIn(0, 99)
                    onProgress(pct)
                }
            }
            cont.invokeOnCancellation { FFmpegKit.cancel() }

            val session = FFmpegKit.executeWithArguments(command)
            FFmpegKitConfig.enableStatisticsCallback(null)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                output.delete()
                val detail = session.failStackTrace ?: session.output ?: "FFmpeg failed"
                Log.e(TAG, "encode failed: $detail")
                if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException(detail))
                }
            } else {
                onProgress(100)
                if (cont.isActive) cont.resume(CompressionResult(output, wasLossless = false))
            }
        }
    }

    private fun buildVideoFilter(input: File, profile: CompressionProfile): String? {
        val tw = profile.targetWidth
        val th = profile.targetHeight
        if (tw != null && th != null) {
            return "scale=$tw:$th"
        }
        val cap = profile.maxVideoLongEdge ?: return null
        val retriever = MediaMetadataRetriever()
        val w: Int
        val h: Int
        try {
            retriever.setDataSource(input.path)
            w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
        } finally {
            retriever.release()
        }
        val longEdge = maxOf(w, h)
        if (longEdge <= cap) return null
        return if (w >= h) "scale=$cap:-2" else "scale=-2:$cap"
    }

    private companion object {
        const val TAG = "FfmpegVideoCompressor"
    }

    private fun readDurationMs(input: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(input.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1L
        } finally {
            retriever.release()
        }
    }
}
