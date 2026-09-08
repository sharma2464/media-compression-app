package com.sharma2464.mediacompression.compress

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.data.Decision
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.scan.FolderScanner
import com.sharma2464.mediacompression.scan.findByRelativePath
import com.sharma2464.mediacompression.scan.hasFullStorageAccess
import com.sharma2464.mediacompression.scan.resolveRealFile
import com.sharma2464.mediacompression.settings.AppSettings
import java.io.File

/**
 * Runs the backup -> compress -> verify -> replace steps for one [FileEntry].
 * Backup always happens first and is never skipped, even if compression later fails.
 */
class CompressionPipeline(private val context: Context) {
    private val settings = AppSettings(context)
    private val dao = AppDatabase.get(context).fileEntryDao()

    suspend fun process(entry: FileEntry) {
        val rootUri = Uri.parse(settings.rootTreeUri ?: return)
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return
        val sourceDoc = findByRelativePath(root, entry.relativePath) ?: return

        // Input and output live in separate directories: compressors write passthrough
        // results as File(workDir, input.name), which would collide with input itself
        // (silently deleting it before reading) if both were in the same directory.
        val inputDir = File(context.cacheDir, "compress_work/in").apply { mkdirs() }
        val workDir = File(context.cacheDir, "compress_work/out").apply { mkdirs() }
        val localCopy = File(inputDir, entry.displayName)
        context.contentResolver.openInputStream(sourceDoc.uri)!!.use { input ->
            localCopy.outputStream().use { input.copyTo(it) }
        }

        backup(root, entry.relativePath, localCopy)

        val compressor = compressorFor(entry.kind)
        val result = compressor.compress(localCopy, settings.compressionMode, workDir)

        // Keep the extension truthful to the actual bytes (e.g. photo.png -> photo.webp)
        // so any app opening the file by extension still reads it correctly.
        val newName = result.outputFile.name
        if (newName != sourceDoc.name) sourceDoc.renameTo(newName)
        replaceInPlace(sourceDoc, result.outputFile, entry.lastModified)

        val newRelativePath = entry.relativePath.substringBeforeLast('/', "").let {
            if (it.isEmpty()) newName else "$it/$newName"
        }
        dao.update(
            entry.copy(
                displayName = newName,
                relativePath = newRelativePath,
                uri = sourceDoc.uri.toString(),
                decision = Decision.DONE,
                compressedSizeBytes = result.outputFile.length(),
                wasLossless = result.wasLossless,
                reviewedAt = System.currentTimeMillis(),
                backupRelativePath = entry.relativePath,
            ),
        )

        localCopy.delete()
        if (result.outputFile != localCopy) result.outputFile.delete()
    }

    private fun backup(root: DocumentFile, relativePath: String, original: File) {
        val backupDoc = ensureBackupPath(root, relativePath)
        context.contentResolver.openOutputStream(backupDoc.uri, "wt")!!.use { out ->
            original.inputStream().use { it.copyTo(out) }
        }
    }

    private fun replaceInPlace(sourceDoc: DocumentFile, newContent: File, originalLastModified: Long) {
        context.contentResolver.openOutputStream(sourceDoc.uri, "wt")!!.use { out ->
            newContent.inputStream().use { it.copyTo(out) }
        }
        // sourceDoc.uri.path is the encoded SAF document path, not a real filesystem path,
        // so this only works (and needs) MANAGE_EXTERNAL_STORAGE to resolve+touch the real file.
        if (hasFullStorageAccess()) {
            runCatching { resolveRealFile(sourceDoc.uri)?.setLastModified(originalLastModified) }
        }
    }

    private fun ensureBackupPath(root: DocumentFile, relativePath: String): DocumentFile {
        val segments = relativePath.split("/")
        val backupDirName = FolderScanner.backupDirName(root)
        var dir = root.findFile(backupDirName) ?: root.createDirectory(backupDirName)!!
        for (segment in segments.dropLast(1)) {
            dir = dir.findFile(segment) ?: dir.createDirectory(segment)!!
        }
        val fileName = segments.last()
        return dir.findFile(fileName) ?: dir.createFile("application/octet-stream", fileName)!!
    }

    private fun compressorFor(kind: FileKind): Compressor = when (kind) {
        FileKind.PHOTO -> PhotoCompressor()
        FileKind.VIDEO -> VideoCompressor(context)
        FileKind.LIVE_PHOTO -> LivePhotoCompressor(context)
        FileKind.PDF -> PdfCompressor(context)
        FileKind.DOCUMENT -> ZipRecompressor()
        FileKind.TEXT, FileKind.OTHER -> TextCompressor()
    }
}
