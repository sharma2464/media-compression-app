package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File

object CompressSettingsEstimator {
    private const val MUX_OVERHEAD_BYTES = 64 * 1024L

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
                (fileSize(f) * p.estimatedSizeRatio(k)).toLong()
            }
        }
        val meta = primaryVideo?.let { VideoMetadataProbe.probe(it) }
        val targetCapBytes = (settings.targetSizeMb * 1024 * 1024).toLong()
            .takeIf { settings.targetSizeMb > 0f }
            ?: settings.platformTarget?.maxBytes

        var total = 0L
        for ((file, kind) in files) {
            val size = fileSize(file)
            val estimate = when (kind) {
                FileKind.VIDEO -> {
                    if (primaryVideo != null && file == primaryVideo && meta != null && meta.durationMs > 0) {
                        plannedVideoBytes(primaryVideo, settings, meta, targetCapBytes, size)
                    } else {
                        val profile = CompressSettingsMapper.toProfile(mode, settings, meta, primaryVideo)
                        var ratio = profile.estimatedSizeRatio(kind)
                        applyCap(ratio, size, targetCapBytes, files, kind)
                    }
                }
                FileKind.PHOTO, FileKind.LIVE_PHOTO -> {
                    val profileFile = if (file == primaryPhoto) file else primaryPhoto
                    val profile = CompressSettingsMapper.toProfile(mode, settings, null, profileFile)
                    var ratio = profile.estimatedSizeRatio(kind)
                    applyCap(ratio, size, targetCapBytes, files, kind)
                }
                else -> {
                    val profile = CompressSettingsMapper.toProfile(mode, settings, meta, primaryVideo)
                    (size * profile.estimatedSizeRatio(kind)).toLong()
                }
            }
            total += estimate.coerceAtLeast(0)
        }
        return total
    }

    private fun plannedVideoBytes(
        file: File,
        settings: CompressJobSettings,
        meta: VideoMetadata,
        targetCapBytes: Long?,
        originalBytes: Long,
    ): Long {
        val targetBytes = (settings.targetSizeMb * 1024 * 1024).toLong()
            .takeIf { settings.targetSizeMb > 0f }
        val planned = VideoEncodePlanner.planWithDuration(file, settings, meta, targetBytes)
        val durationSec = meta.durationMs / 1000.0
        val audioBps = if (settings.removeAudio) 0 else planned.audioBitrateBps
        val videoBps = planned.videoBitrateBps
        val raw = ((videoBps + audioBps) * durationSec / 8.0).toLong() + MUX_OVERHEAD_BYTES
        val capped = targetCapBytes?.let { minOf(raw, it, originalBytes) } ?: minOf(raw, originalBytes)
        return capped.coerceAtLeast(0)
    }

    private fun applyCap(
        ratio: Double,
        size: Long,
        capBytes: Long?,
        files: List<Pair<File, FileKind?>>,
        kind: FileKind?,
    ): Long {
        var r = ratio
        if (capBytes != null) {
            val sameKindCount = when (kind) {
                FileKind.VIDEO -> files.count { it.second == FileKind.VIDEO }.coerceAtLeast(1)
                FileKind.PHOTO, FileKind.LIVE_PHOTO ->
                    files.count { it.second == FileKind.PHOTO || it.second == FileKind.LIVE_PHOTO }.coerceAtLeast(1)
                else -> 1
            }
            val capPerFile = capBytes / sameKindCount
            r = minOf(r, capPerFile.toDouble() / size.coerceAtLeast(1))
        }
        return (size * r).toLong()
    }

    private fun fileSize(file: File): Long =
        if (file.isDirectory) file.walk().filter { it.isFile }.sumOf { it.length() } else file.length()
}
