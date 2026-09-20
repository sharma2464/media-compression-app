package com.sharma2464.mediacompression.compress

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlinx.coroutines.CancellationException

class CompressionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).fileEntryDao()
        val pipeline = CompressionPipeline(applicationContext)
        val settings = AppSettings(applicationContext)
        val queued = dao.queuedForCompression()
        if (queued.isEmpty()) return Result.success()

        val modeLabel = when (settings.compressionMode) {
            CompressionMode.LOSSLESS_ONLY -> "Lossless only"
            CompressionMode.ADAPTIVE -> "Adaptive"
        }
        CompressionStatus.startBatch(queued.map { it.displayName to it.sizeBytes }, modeLabel)

        try {
            for ((index, entry) in queued.withIndex()) {
                CompressionStatus.updateCurrentFile(index, percent = 0, rateBytesPerSec = 0)
                CompressionForegroundService.updateNotification(applicationContext, index + 1, queued.size, entry.displayName)

                var lastProcessedBytes = 0L
                var lastTickMs = System.currentTimeMillis()
                try {
                    pipeline.process(entry) { percent ->
                        val now = System.currentTimeMillis()
                        val processedBytes = entry.sizeBytes * percent / 100
                        val dtMs = (now - lastTickMs).coerceAtLeast(1)
                        val rate = (processedBytes - lastProcessedBytes) * 1000 / dtMs
                        lastProcessedBytes = processedBytes
                        lastTickMs = now
                        CompressionStatus.updateCurrentFile(index, percent, rate)
                    }
                    CompressionStatus.markDone(index)
                } catch (e: CancellationException) {
                    CompressionStatus.markCancelled(index)
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace() // leaves entry as COMPRESS so it's retried next run
                    CompressionStatus.markFailed(index)
                }
            }
        } finally {
            CompressionStatus.clear()
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "compression_pipeline"
    }
}
