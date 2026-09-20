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
    ): Long {
        if (mode == CompressionMode.LOSSLESS_ONLY) {
            return files.sumOf { (f, k) ->
                val p = CompressionProfile.resolve(mode, CompressionStrength.BALANCED)
                (f.length() * p.estimatedSizeRatio(k)).toLong()
            }
        }
        val meta = primaryVideo?.let { VideoMetadataProbe.probe(it) }
        val profile = CompressSettingsMapper.toProfile(mode, settings, meta, primaryVideo)
        var total = 0L
        val capBytes = (settings.targetSizeMb * 1024 * 1024).toLong()
            .takeIf { settings.targetSizeMb > 0f }
            ?: settings.platformTarget?.maxBytes
        for ((file, kind) in files) {
            val size = if (file.isDirectory) file.walk().filter { it.isFile }.sumOf { it.length() } else file.length()
            var ratio = profile.estimatedSizeRatio(kind)
            if (kind == FileKind.VIDEO && capBytes != null) {
                val videoCount = files.count { it.second == FileKind.VIDEO }.coerceAtLeast(1)
                val capPerFile = capBytes / videoCount
                ratio = minOf(ratio, capPerFile.toDouble() / size.coerceAtLeast(1))
            }
            total += (size * ratio).toLong().coerceAtLeast(0)
        }
        return total
    }
}
