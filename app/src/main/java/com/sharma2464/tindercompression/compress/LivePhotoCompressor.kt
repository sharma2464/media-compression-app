package com.sharma2464.tindercompression.compress

import com.sharma2464.tindercompression.settings.CompressionMode
import java.io.File

/**
 * Motion Photos (Google: single JPEG/HEIC with an MP4 appended + XMP offset tag) and
 * Live Photos (Apple: paired HEIC/JPEG + MOV sharing a content-identifier tag) bundle
 * a photo and a video together — compressing either half naively breaks the pairing.
 *
 * ponytail: not implemented yet — detection (XMP `MotionPhoto`/`MicroVideoOffset` byte
 * offset for Google; `com.apple.quicktime.content.identifier` pairing for Apple) needs
 * testing against real device samples rather than a guessed byte-offset implementation.
 * For now this passes the file through unchanged (0% saving) so a live/motion photo is
 * never silently corrupted by the generic PhotoCompressor. Tracked as a follow-up issue:
 * split → compress each half via PhotoCompressor/VideoCompressor → re-splice + rewrite
 * the offset/identifier tag so the OS still recognizes the pairing.
 */
class LivePhotoCompressor : Compressor {
    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        val output = File(workDir, input.name)
        input.copyTo(output, overwrite = true)
        return CompressionResult(output, wasLossless = true)
    }
}
