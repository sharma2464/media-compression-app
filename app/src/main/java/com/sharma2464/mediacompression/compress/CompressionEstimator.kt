package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File

object CompressionEstimator {
    fun estimatedBytesAfter(file: File, kind: FileKind?, mode: CompressionMode): Long {
        val size = if (file.isDirectory) {
            file.walk().filter { it.isFile }.sumOf { it.length() }
        } else {
            file.length()
        }
        val ratio = ratioFor(kind, mode)
        return (size * ratio).toLong().coerceAtLeast(0L)
    }

    fun estimatedBytesAfter(files: List<Pair<File, FileKind?>>, mode: CompressionMode): Long =
        files.sumOf { (file, kind) -> estimatedBytesAfter(file, kind, mode) }

    private fun ratioFor(kind: FileKind?, mode: CompressionMode): Double = when (mode) {
        CompressionMode.LOSSLESS_ONLY -> when (kind) {
            FileKind.VIDEO -> 0.95
            FileKind.PHOTO, FileKind.LIVE_PHOTO -> 0.98
            else -> 0.99
        }
        CompressionMode.ADAPTIVE -> when (kind) {
            FileKind.VIDEO -> 0.55
            FileKind.PHOTO, FileKind.LIVE_PHOTO -> 0.65
            FileKind.PDF -> 0.75
            FileKind.DOCUMENT -> 0.85
            FileKind.TEXT -> 0.5
            else -> 0.9
        }
    }
}
