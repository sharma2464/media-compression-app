package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File

object CompressionEstimator {
    fun estimatedBytesAfter(
        file: File,
        kind: FileKind?,
        mode: CompressionMode,
        strength: CompressionStrength = CompressionStrength.BALANCED,
    ): Long {
        val size = if (file.isDirectory) {
            file.walk().filter { it.isFile }.sumOf { it.length() }
        } else {
            file.length()
        }
        val profile = CompressionProfile.resolve(mode, strength)
        val ratio = profile.estimatedSizeRatio(kind)
        return (size * ratio).toLong().coerceAtLeast(0L)
    }

    fun estimatedBytesAfter(files: List<Pair<File, FileKind?>>, mode: CompressionMode, strength: CompressionStrength): Long =
        files.sumOf { (file, kind) -> estimatedBytesAfter(file, kind, mode, strength) }

    fun estimatedBytesAfter(files: List<Pair<File, FileKind?>>, profile: CompressionProfile): Long =
        files.sumOf { (file, kind) ->
            val size = if (file.isDirectory) {
                file.walk().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
            (size * profile.estimatedSizeRatio(kind)).toLong().coerceAtLeast(0L)
        }
}
