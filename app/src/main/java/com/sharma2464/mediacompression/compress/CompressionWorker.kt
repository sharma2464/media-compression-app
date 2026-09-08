package com.sharma2464.mediacompression.compress

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharma2464.mediacompression.data.AppDatabase

class CompressionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).fileEntryDao()
        val pipeline = CompressionPipeline(applicationContext)
        val queued = dao.queuedForCompression()
        for ((index, entry) in queued.withIndex()) {
            CompressionForegroundService.updateProgress(applicationContext, index + 1, queued.size, entry.displayName)
            runCatching { pipeline.process(entry) }
                .onFailure { it.printStackTrace() } // leaves entry as COMPRESS so it's retried next run
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "compression_pipeline"
    }
}
