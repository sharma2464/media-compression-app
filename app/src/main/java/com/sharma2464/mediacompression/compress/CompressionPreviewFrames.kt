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

object CompressionPreviewFrames {
    private const val MAX_PREVIEW_EDGE = 720

    fun positionLabel(percent: Int, durationMs: Long?): String? {
        if (durationMs == null || durationMs <= 0) return null
        val positionMs = (percent.coerceIn(0, 100) / 100f * durationMs).toLong()
        val totalSec = durationMs / 1000
        val posSec = positionMs / 1000
        return "Frame ~${formatTime(posSec)} / ${formatTime(totalSec)}"
    }

    suspend fun loadVideoFrame(
        context: Context,
        uriString: String?,
        percent: Int,
        durationMs: Long?,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        val duration = durationMs ?: return@withContext null
        if (duration <= 0) return@withContext null
        val timeUs = (percent.coerceIn(0, 100) / 100f * duration * 1000).toLong()
        val retriever = MediaMetadataRetriever()
        try {
            when {
                uriString.startsWith("file://") -> retriever.setDataSource(uriString.removePrefix("file://"))
                else -> retriever.setDataSource(context, Uri.parse(uriString))
            }
            downscale(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
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
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isNullOrBlank()) return@withContext null
        val file = File(filePath)
        if (!file.exists() || file.length() < 1024) return@withContext null
        val duration = durationMs ?: return@withContext null
        if (duration <= 0) return@withContext null
        val timeUs = (percent.coerceIn(0, 100) / 100f * duration * 1000).toLong()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            downscale(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

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
