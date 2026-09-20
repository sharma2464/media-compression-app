package com.sharma2464.mediacompression.compress

import android.content.Context
import android.net.Uri
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.data.Decision
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.scan.classifyFile
import com.sharma2464.mediacompression.scan.guessMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun enqueueCompression(context: Context, selected: Set<File>) {
    withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).fileEntryDao()
        val entries = mutableListOf<FileEntry>()
        selected.forEach { file ->
            if (file.isDirectory) {
                file.walk().filter { it.isFile }.forEach { f ->
                    entries += fileEntryFrom(f)
                }
            } else {
                entries += fileEntryFrom(file)
            }
        }
        dao.insertAll(entries)
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
