package com.sharma2464.tindercompression.compress

import com.sharma2464.tindercompression.settings.CompressionMode
import java.io.File

data class CompressionResult(val outputFile: File, val wasLossless: Boolean)

/**
 * Compresses [input] into a new temp file and reports whether the result is
 * bit-exact lossless. Never mutates [input] — the caller owns backup/replace.
 */
interface Compressor {
    suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult
}
