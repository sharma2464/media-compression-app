package com.sharma2464.tindercompression.compress

import android.content.Context
import com.sharma2464.tindercompression.settings.CompressionMode
import java.io.File

/**
 * Motion Photos (Google: single JPEG/HEIC with an MP4 appended + XMP offset tag) and
 * Live Photos (Apple: paired HEIC/JPEG + MOV sharing a content-identifier tag) bundle
 * a photo and a video together — compressing either half naively breaks the pairing.
 *
 * Google Motion Photo (JPEG-based) is handled via [MotionPhotoSplicer]: split off the
 * appended video, recompress it with [VideoCompressor], re-splice with the XMP length
 * patched in place. The primary JPEG's pixels/EXIF are never touched. Any failure to
 * parse, split, or validate the round trip falls back to an unchanged copy-through —
 * see [MotionPhotoSplicer] for why (no real device-captured sample to test against).
 *
 * ponytail: Apple Live Photo pairing (matching `content.identifier` between a HEIC/JPEG
 * and a sibling .MOV) and HEIC/AVIF-based Motion Photos (`mpvd` ISOBMFF box) aren't
 * implemented yet — both still pass through unchanged. Tracked in issue #15.
 */
class LivePhotoCompressor(private val context: Context) : Compressor {
    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        val passthrough = { copyThrough(input, workDir) }
        if (mode == CompressionMode.LOSSLESS_ONLY) return passthrough()

        val bytes = input.readBytes()
        val ref = MotionPhotoSplicer.findVideoLength(bytes) ?: return passthrough()
        val videoLength = MotionPhotoSplicer.videoLength(bytes, ref) ?: return passthrough()
        val (primary, video) = MotionPhotoSplicer.split(bytes, videoLength) ?: return passthrough()

        val videoFile = File(workDir, "${input.nameWithoutExtension}_embedded.mp4").apply { writeBytes(video) }
        val compressedVideo = runCatching { VideoCompressor(context).compress(videoFile, mode, workDir) }
            .getOrNull() ?: return passthrough()
        val newVideoBytes = compressedVideo.outputFile.readBytes()
        videoFile.delete()
        if (compressedVideo.outputFile != videoFile) compressedVideo.outputFile.delete()

        val spliced = MotionPhotoSplicer.reassemble(primary, ref, newVideoBytes.size, newVideoBytes)
            ?: return passthrough()

        val output = File(workDir, input.name)
        output.writeBytes(spliced)
        return CompressionResult(output, wasLossless = false)
    }

    private fun copyThrough(input: File, workDir: File): CompressionResult {
        val output = File(workDir, input.name)
        input.copyTo(output, overwrite = true)
        return CompressionResult(output, wasLossless = true)
    }
}
