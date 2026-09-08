package com.sharma2464.tindercompression.compress

import com.sharma2464.tindercompression.settings.CompressionMode
import java.io.File

/**
 * Catch-all for text and unrecognized binary files.
 *
 * ponytail: wrapping the file in Deflate would shrink it, but the output would no
 * longer open as a plain text/binary file in whatever app expects the original
 * extension — that violates "stays exactly the same except size". There's no safe
 * transparent way to shrink an arbitrary file while keeping it directly openable,
 * so this pass-through just reports 0% savings honestly instead of faking a win.
 * Upgrade path: filesystem-level transparent compression (e.g. per-file Btrfs/exFAT
 * compression) if that's ever wired up, since that shrinks storage without touching
 * file contents/extension at all.
 */
class TextCompressor : Compressor {
    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        val output = File(workDir, input.name)
        input.copyTo(output, overwrite = true)
        return CompressionResult(output, wasLossless = true)
    }
}
