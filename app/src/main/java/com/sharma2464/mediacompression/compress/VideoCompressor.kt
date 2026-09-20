package com.sharma2464.mediacompression.compress

import android.content.Context
import android.util.Log
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File

/**
 * Video compression facade: Media3 hardware transcode for most presets; FFmpeg libx265
 * for [CompressionStrength.SMALLEST] with Media3 fallback.
 */
class VideoCompressor(private val context: Context) : Compressor {
    private val media3 = Media3VideoCompressor(context)
    private val ffmpeg = FfmpegVideoCompressor()

    override suspend fun compress(
        input: File,
        profile: CompressionProfile,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionResult {
        if (profile.mode == CompressionMode.ADAPTIVE && profile.preferFfmpeg) {
            return runCatching {
                preserveVideoMetadata(input, workDir, ffmpeg.compress(input, profile, workDir, onProgress))
            }.getOrElse { error ->
                Log.w(TAG, "Smallest-file FFmpeg encode failed; using hardware Smaller-file preset", error)
                preserveVideoMetadata(
                    input,
                    workDir,
                    media3.compress(
                        input,
                        CompressionProfile.forSmallestFallback(profile.mode),
                        workDir,
                        onProgress,
                    ),
                )
            }
        }
        return preserveVideoMetadata(input, workDir, media3.compress(input, profile, workDir, onProgress))
    }

    private fun preserveVideoMetadata(
        original: File,
        workDir: File,
        result: CompressionResult,
    ): CompressionResult {
        if (result.wasLossless) return result
        val merged = VideoMetadataPreserver.mergeEncodedWithOriginalMetadata(
            original,
            result.outputFile,
            workDir,
        )
        return CompressionResult(merged, wasLossless = false)
    }

    private companion object {
        const val TAG = "VideoCompressor"
    }
}
