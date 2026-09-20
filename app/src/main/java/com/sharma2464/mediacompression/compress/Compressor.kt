package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File

data class CompressionResult(val outputFile: File, val wasLossless: Boolean)

/**
 * Compresses [input] into a new temp file and reports whether the result is
 * bit-exact lossless. Never mutates [input] — the caller owns backup/replace.
 *
 * [onProgress] reports 0-100; compressors that can't measure progress (most of them —
 * only the video transformer exposes it) simply never call it, and callers should treat
 * "no calls" as indeterminate progress rather than stuck-at-zero.
 */
interface Compressor {
    suspend fun compress(
        input: File,
        mode: CompressionMode,
        workDir: File,
        onProgress: (Int) -> Unit = {},
    ): CompressionResult
}
