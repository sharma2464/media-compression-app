package com.sharma2464.mediacompression.compress

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.data.Decision
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.scan.classifyFile
import com.sharma2464.mediacompression.scan.guessMimeType
import com.sharma2464.mediacompression.scan.isCompressibleMedia
import com.sharma2464.mediacompression.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun enqueueCompression(context: Context, selected: Set<File>) {
    withContext(Dispatchers.IO) {
        val settings = AppSettings(context)
        val job = settings.sessionCompressJobSettings ?: CompressJobSettings.DEFAULT
        val strength = settings.sessionCompressionStrength ?: settings.compressionStrength
        val dao = AppDatabase.get(context).fileEntryDao()
        val entries = mutableListOf<FileEntry>()
        selected.forEach { file ->
            if (file.isDirectory) {
                file.walk().filter { it.isFile }.forEach { f ->
                    if (isCompressibleMedia(classifyFile(guessMimeType(f.name)))) {
                        entries += fileEntryFrom(f)
                    }
                }
            } else if (isCompressibleMedia(classifyFile(guessMimeType(file.name)))) {
                entries += fileEntryFrom(file)
            }
        }
        if (entries.isEmpty()) return@withContext
        dao.insertAll(entries)
        val work = OneTimeWorkRequestBuilder<CompressionWorker>()
            .setInputData(
                Data.Builder()
                    .putString(CompressionWorker.INPUT_STRENGTH, strength.name)
                    .putString(CompressionWorker.INPUT_JOB_SETTINGS, job.toJson())
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CompressionWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            work,
        )
        CompressionForegroundService.start(context)
    }
}

private fun fileEntryFrom(file: File): FileEntry {
    val mime = guessMimeType(file.name)
    val kind = classifyFile(mime)
    return FileEntry(
        uri = Uri.fromFile(file).toString(),
        relativePath = file.absolutePath,
        displayName = file.name,
        mimeType = mime,
        kind = kind,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        decision = Decision.COMPRESS,
    )
}
