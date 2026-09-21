package com.sharma2464.mediacompression.compress

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.compress.CompressionStrength
import kotlinx.coroutines.CancellationException

class CompressionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).fileEntryDao()
        val pipeline = CompressionPipeline(applicationContext)
        val settings = AppSettings(applicationContext)
        inputData.getString(INPUT_STRENGTH)?.let { name ->
            runCatching { CompressionStrength.valueOf(name) }.getOrNull()?.let {
                settings.sessionCompressionStrength = it
            }
        }
        CompressJobSettings.fromJson(inputData.getString(INPUT_JOB_SETTINGS))?.let {
            settings.sessionCompressJobSettings = it
        }
        val queued = dao.queuedForCompression()
        if (queued.isEmpty()) return Result.success()

        val job = settings.sessionCompressJobSettings ?: CompressJobSettings.DEFAULT
        val modeLabel = CompressSettingsMapper.summaryLabel(job)
        CompressionStatus.startBatch(
            queued.map { it.displayName to it.sizeBytes },
            modeLabel,
            jobSettings = job,
            videoEngine = settings.videoEngine,
        )

        val finishedItems = mutableListOf<CompressionFinishedItem>()
        try {
            for ((index, entry) in queued.withIndex()) {
                if (CompressionStatus.cancelRequested.value) {
                    break
                }
                val durationMs = if (entry.kind == FileKind.VIDEO) {
                    VideoMetadataProbe.probeUri(entry.uri)?.durationMs
                } else {
                    null
                }
                CompressionStatus.updateCurrentFile(
                    index,
                    percent = 0,
                    rateBytesPerSec = 0,
                    fileUri = entry.uri,
                    fileKind = entry.kind,
                    durationMs = durationMs,
                )
                CompressionForegroundService.updateNotification(applicationContext, index + 1, queued.size, entry.displayName)

                var lastProcessedBytes = 0L
                var lastTickMs = System.currentTimeMillis()
                try {
                    val output = pipeline.process(entry, fileIndex = index) { percent ->
                        val now = System.currentTimeMillis()
                        val processedBytes = entry.sizeBytes * percent / 100
                        val dtMs = (now - lastTickMs).coerceAtLeast(1)
                        val rate = (processedBytes - lastProcessedBytes) * 1000 / dtMs
                        lastProcessedBytes = processedBytes
                        lastTickMs = now
                        CompressionStatus.updateCurrentFile(
                            index,
                            percent,
                            rate,
                            fileUri = entry.uri,
                            fileKind = entry.kind,
                            durationMs = durationMs,
                        )
                    }
                    output?.let { result ->
                        finishedItems += CompressionFinishedItem(
                            displayName = result.destinationFile.name,
                            outputPath = result.destinationFile.absolutePath,
                            originalBytes = result.originalBytes,
                            compressedBytes = result.compressedBytes,
                            isVideo = result.isVideo,
                            sourceUri = entry.uri,
                            fileKind = entry.kind,
                        )
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
            settings.sessionDestinationTreeUri = null
            settings.sessionCompressionStrength = null
            settings.sessionCompressJobSettings = null
            if (finishedItems.isNotEmpty()) {
                CompressionStatus.complete(
                    CompressionFinishedSummary(
                        items = finishedItems,
                        modeLabel = modeLabel,
                    ),
                )
            } else {
                CompressionStatus.clear()
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "compression_pipeline"
        const val INPUT_STRENGTH = "compression_strength"
        const val INPUT_JOB_SETTINGS = "compression_job_settings"
    }
}
