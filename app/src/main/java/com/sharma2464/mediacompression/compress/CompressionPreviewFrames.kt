package com.sharma2464.mediacompression.compress

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.sharma2464.mediacompression.data.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

object CompressionPreviewFrames {
    private const val MAX_PREVIEW_EDGE = 720
    private const val MIN_ENCODED_BYTES = 256L

    fun recycleIfReplaced(old: Bitmap?, new: Bitmap?) {
        if (old != null && old != new && !old.isRecycled) {
            old.recycle()
        }
    }

    fun positionLabel(percent: Int, durationMs: Long?): String? {
        if (durationMs == null || durationMs <= 0) return null
        val positionMs = (percent.coerceIn(0, 100) / 100f * durationMs).toLong()
        val totalSec = durationMs / 1000
        val posSec = positionMs / 1000
        return "Frame ~${formatTime(posSec)} / ${formatTime(totalSec)}"
    }

    fun resolveCompressedPreviewPath(finishedOutputPath: String?, encodeOutputPath: String?): String? =
        listOfNotNull(finishedOutputPath, encodeOutputPath)
            .map(::File)
            .filter { it.exists() && it.length() >= MIN_ENCODED_BYTES }
            .maxByOrNull { it.length() }
            ?.absolutePath

    /** Maps source timeline position to a seek time inside a possibly partial encoded file. */
    fun encodedSeekTimeUs(
        sourceDurationMs: Long,
        timelinePercent: Int,
        encodeProgressPercent: Int,
        encodedDurationMs: Long?,
    ): Long {
        val timelineMs = timelinePercent.coerceIn(0, 100) / 100f * sourceDurationMs
        val encodedCapMs = when {
            encodedDurationMs != null && encodedDurationMs > 0 ->
                min(encodedDurationMs.toFloat(), sourceDurationMs * encodeProgressPercent / 100f)
            else -> sourceDurationMs * encodeProgressPercent / 100f
        }.coerceAtLeast(1f)
        val seekMs = min(timelineMs, encodedCapMs * 0.92f).coerceAtLeast(0f)
        return (seekMs * 1000).toLong()
    }

    fun compareSeekTimeUs(
        sourceDurationMs: Long,
        timelinePercent: Int,
        encodeProgressPercent: Int,
        encodedDurationMs: Long?,
        useEncodeCap: Boolean,
    ): Long {
        if (!useEncodeCap) {
            return (timelinePercent.coerceIn(0, 100) / 100f * sourceDurationMs * 1000).toLong()
        }
        return encodedSeekTimeUs(
            sourceDurationMs,
            timelinePercent,
            encodeProgressPercent,
            encodedDurationMs,
        )
    }

    suspend fun loadVideoFrameAtTimeUs(
        context: Context,
        uriString: String?,
        timeUs: Long,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            when {
                uriString.startsWith("file://") -> retriever.setDataSource(uriString.removePrefix("file://"))
                else -> retriever.setDataSource(context, Uri.parse(uriString))
            }
            downscale(
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC),
            ) ?: downscale(
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST),
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun loadVideoFrame(
        context: Context,
        uriString: String?,
        percent: Int,
        durationMs: Long?,
    ): Bitmap? {
        val duration = durationMs ?: return null
        if (duration <= 0) return null
        val timeUs = (percent.coerceIn(0, 100) / 100f * duration * 1000).toLong()
        return loadVideoFrameAtTimeUs(context, uriString, timeUs)
    }

    suspend fun loadEncodedFrameAtTimeUs(
        filePath: String,
        timeUs: Long,
        allowFallbacks: Boolean = true,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || file.length() < MIN_ENCODED_BYTES) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val times = if (allowFallbacks) {
                listOf(timeUs, (timeUs * 0.5f).toLong(), 0L).distinct()
            } else {
                listOf(timeUs)
            }
            for (t in times) {
                val frame = retriever.getFrameAtTime(
                    t,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)
                val scaled = downscale(frame)
                if (scaled != null) return@withContext scaled
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    data class CompareFrameLoad(
        val original: Bitmap?,
        val compressed: Bitmap?,
        val compressedIsSynthetic: Boolean,
        val seekTimeUs: Long,
        val encodedDurationMs: Long?,
    )

    suspend fun loadCompareFrames(
        context: Context,
        sourceUri: String?,
        encodedPath: String?,
        sourceDurationMs: Long?,
        timelinePercent: Int,
        encodeProgressPercent: Int,
        encodingInProgress: Boolean,
        jobSettings: CompressJobSettings?,
        videoMeta: VideoMetadata?,
        syntheticFromOriginal: (Bitmap, VideoMetadata, CompressJobSettings) -> Bitmap,
    ): CompareFrameLoad = withContext(Dispatchers.IO) {
        val duration = sourceDurationMs ?: return@withContext CompareFrameLoad(null, null, false, 0L, null)
        if (duration <= 0) return@withContext CompareFrameLoad(null, null, false, 0L, null)

        val encodedDurationMs = encodedPath?.let { path ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }

        val useEncodeCap = encodingInProgress && encodedPath != null
        val primarySeekUs = compareSeekTimeUs(
            duration,
            timelinePercent,
            encodeProgressPercent,
            encodedDurationMs,
            useEncodeCap,
        )
        val seekCandidates = listOf(
            primarySeekUs,
            (primarySeekUs * 0.5f).toLong(),
            0L,
        ).distinct()

        var original: Bitmap? = null
        var compressed: Bitmap? = null
        var synthetic = false
        var usedSeekUs = primarySeekUs
        var fallbackOriginal: Bitmap? = null

        for (seekUs in seekCandidates) {
            val orig = loadVideoFrameAtTimeUs(context, sourceUri, seekUs) ?: continue
            when {
                encodedPath != null -> {
                    val encoded = loadEncodedFrameAtTimeUs(
                        encodedPath,
                        seekUs,
                        allowFallbacks = false,
                    )
                    if (encoded != null) {
                        fallbackOriginal?.recycle()
                        original = orig
                        compressed = encoded
                        synthetic = false
                        usedSeekUs = seekUs
                        break
                    }
                    fallbackOriginal?.recycle()
                    fallbackOriginal = orig
                }
                else -> {
                    fallbackOriginal?.recycle()
                    original = orig
                    usedSeekUs = seekUs
                    break
                }
            }
        }

        if (original == null && fallbackOriginal != null) {
            original = fallbackOriginal
            fallbackOriginal = null
        } else {
            fallbackOriginal?.recycle()
        }

        if (compressed == null && original != null && jobSettings != null && videoMeta != null) {
            compressed = syntheticFromOriginal(original, videoMeta, jobSettings)
            synthetic = true
        }

        if (original != null && compressed != null && !synthetic) {
            val o = original
            val c = compressed
            if (o.width != c.width || o.height != c.height) {
                compressed = Bitmap.createScaledBitmap(c, o.width, o.height, true)
                if (compressed !== c) c.recycle()
            }
        }

        CompareFrameLoad(
            original = original,
            compressed = compressed,
            compressedIsSynthetic = synthetic,
            seekTimeUs = usedSeekUs,
            encodedDurationMs = encodedDurationMs,
        )
    }

    suspend fun loadEncodedCompareFrame(
        filePath: String?,
        sourceDurationMs: Long?,
        timelinePercent: Int,
        encodeProgressPercent: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isNullOrBlank() || sourceDurationMs == null || sourceDurationMs <= 0) {
            return@withContext null
        }
        val file = File(filePath)
        if (!file.exists() || file.length() < MIN_ENCODED_BYTES) return@withContext null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val encodedDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val primaryUs = encodedSeekTimeUs(
                sourceDurationMs,
                timelinePercent,
                encodeProgressPercent,
                encodedDurationMs,
            )
            val fallbacksUs = listOf(
                primaryUs,
                (primaryUs * 0.5f).toLong(),
                0L,
            ).distinct()
            for (timeUs in fallbacksUs) {
                val frame = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                val scaled = downscale(frame)
                if (scaled != null) return@withContext scaled
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun loadVideoFrameFromPath(
        filePath: String?,
        percent: Int,
        durationMs: Long?,
    ): Bitmap? = loadEncodedCompareFrame(filePath, durationMs, percent, encodeProgressPercent = 100)

    fun uriForPreview(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        if (uriString.startsWith("file://")) {
            val f = File(uriString.removePrefix("file://"))
            if (f.exists()) return uriString
        }
        return uriString
    }

    fun isPhotoKind(kind: FileKind?): Boolean =
        kind == FileKind.PHOTO || kind == FileKind.LIVE_PHOTO

    private fun downscale(source: Bitmap?): Bitmap? {
        if (source == null) return null
        val w = source.width
        val h = source.height
        val maxEdge = max(w, h)
        if (maxEdge <= MAX_PREVIEW_EDGE) return source
        val scale = MAX_PREVIEW_EDGE.toFloat() / maxEdge
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true).also {
            if (it != source) source.recycle()
        }
    }

    private fun formatTime(totalSec: Long): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) String.format("%d:%02d", m, s) else "${s}s"
    }
}
