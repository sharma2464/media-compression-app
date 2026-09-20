package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File

object CompressSettingsEstimator {
    fun estimatedBytesAfter(
        files: List<Pair<File, FileKind?>>,
        mode: CompressionMode,
        settings: CompressJobSettings,
        primaryVideo: File?,
        primaryPhoto: File? = null,
    ): Long {
        if (mode == CompressionMode.LOSSLESS_ONLY) {
            return files.sumOf { (f, k) ->
                val p = CompressionProfile.resolve(mode, CompressionStrength.BALANCED)
                (f.length() * p.estimatedSizeRatio(k)).toLong()
            }
        }
        val meta = primaryVideo?.let { VideoMetadataProbe.probe(it) }
        val profileFile = primaryVideo ?: primaryPhoto
        val profile = CompressSettingsMapper.toProfile(mode, settings, meta, profileFile)
        var total = 0L
        val capBytes = (settings.targetSizeMb * 1024 * 1024).toLong()
            .takeIf { settings.targetSizeMb > 0f }
            ?: settings.platformTarget?.maxBytes
        for ((file, kind) in files) {
            val size = if (file.isDirectory) file.walk().filter { it.isFile }.sumOf { it.length() } else file.length()
            var ratio = profile.estimatedSizeRatio(kind)
            if (capBytes != null) {
                val sameKindCount = when (kind) {
                    FileKind.VIDEO -> files.count { it.second == FileKind.VIDEO }.coerceAtLeast(1)
                    FileKind.PHOTO, FileKind.LIVE_PHOTO ->
                        files.count { it.second == FileKind.PHOTO || it.second == FileKind.LIVE_PHOTO }.coerceAtLeast(1)
                    else -> 1
                }
                val capPerFile = capBytes / sameKindCount
                ratio = minOf(ratio, capPerFile.toDouble() / size.coerceAtLeast(1))
            }
            total += (size * ratio).toLong().coerceAtLeast(0)
        }
        return total
    }
}
