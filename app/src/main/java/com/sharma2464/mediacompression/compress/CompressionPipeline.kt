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
import com.sharma2464.mediacompression.settings.StorageMode
import java.io.File
import android.webkit.MimeTypeMap

/**
 * Runs the backup -> compress -> verify -> replace steps for one [FileEntry].
 * Backup always happens first and is never skipped, even if compression later fails.
 */
class CompressionPipeline(private val context: Context) {
    private val settings = AppSettings(context)
    private val dao = AppDatabase.get(context).fileEntryDao()

    private fun profileFor(entry: FileEntry, localFile: File): CompressionProfile {
        val job = settings.sessionCompressJobSettings ?: CompressJobSettings.DEFAULT
        val meta = if (entry.kind == FileKind.VIDEO) {
            VideoMetadataProbe.probe(localFile)
        } else {
            null
        }
        val fileForMapper = when (entry.kind) {
            FileKind.VIDEO -> localFile
            FileKind.PHOTO, FileKind.LIVE_PHOTO -> localFile
            else -> null
        }
        return CompressSettingsMapper.toProfile(settings.compressionMode, job, meta, fileForMapper)
    }

    suspend fun process(entry: FileEntry, onProgress: (Int) -> Unit = {}): CompressionProcessResult? {
        val entryUri = Uri.parse(entry.uri)

        // Input and output live in separate directories: compressors write passthrough
        // results as File(workDir, input.name), which would collide with input itself
        // (silently deleting it before reading) if both were in the same directory.
        val inputDir = File(context.cacheDir, "compress_work/in").apply { mkdirs() }
        val workDir = File(context.cacheDir, "compress_work/out").apply { mkdirs() }
        val localCopy = File(inputDir, entry.displayName)
        val stagingTotal = entry.sizeBytes.coerceAtLeast(1L)

        // Source can be either SAF (content://) or real file (file://)
        if (entryUri.scheme == "file") {
            val sourceFile = File(entryUri.path!!)
            if (!sourceFile.exists() || !sourceFile.canRead()) return null
            copyFileWithProgress(sourceFile, localCopy) { pct ->
                onProgress(CompressionProgressPhases.stagingPercent(stagingTotal * pct / 100, stagingTotal))
            }
            return processRealFile(entry, sourceFile, localCopy, workDir, onProgress)
        } else {
            // SAF path (original)
            val rootUri = Uri.parse(settings.rootTreeUri ?: return null)
            val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            val sourceDoc = findByRelativePath(root, entry.relativePath) ?: return null
            context.contentResolver.openInputStream(sourceDoc.uri)!!.use { input ->
                localCopy.outputStream().use { output ->
                    copyStreamWithProgress(input, output, stagingTotal) { pct ->
                        onProgress(CompressionProgressPhases.stagingPercent(stagingTotal * pct / 100, stagingTotal))
                    }
                }
            }
            return processSafFile(entry, root, sourceDoc, localCopy, workDir, onProgress)
        }
    }

    private suspend fun processSafFile(
        entry: FileEntry,
        root: DocumentFile,
        sourceDoc: DocumentFile,
        localCopy: File,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionProcessResult? {
        val storageMode = settings.storageMode
        if (storageMode == StorageMode.REPLACE_IN_PLACE) {
            backupOriginal(root, entry.relativePath, localCopy)
        }

        val profile = profileFor(entry, localCopy)
        val compressor = compressorFor(entry.kind)
        val result = compressor.compress(localCopy, profile, workDir) { enc ->
            onProgress(CompressionProgressPhases.compressPercent(enc))
        }
        val newName = result.outputFile.name
        val saveTotal = result.outputFile.length().coerceAtLeast(1L)

        val destinationFile = when (settings.storageMode) {
            StorageMode.REPLACE_IN_PLACE -> {
                backupRealFile(File(entry.relativePath), localCopy)
                File(File(entry.relativePath).parent ?: return null, newName).apply {
                    copyFileWithProgress(result.outputFile, this) { pct ->
                        onProgress(CompressionProgressPhases.savingPercent(saveTotal * pct / 100, saveTotal))
                    }
                }
            }
            StorageMode.COMPRESSED_COPY -> {
                val compressedDir = File(File(entry.relativePath).parent ?: return null, "COMPRESSED")
                compressedDir.mkdirs()
                File(compressedDir, newName).apply {
                    copyFileWithProgress(result.outputFile, this) { pct ->
                        onProgress(CompressionProgressPhases.savingPercent(saveTotal * pct / 100, saveTotal))
                    }
                }
            }
        }

        MediaMetadataPreserver.restoreFilesystemTimestamps(localCopy, destinationFile)

        dao.update(
            entry.copy(
                displayName = newName,
                relativePath = destinationFile.absolutePath,
                uri = Uri.fromFile(destinationFile).toString(),
                decision = Decision.DONE,
                compressedSizeBytes = result.outputFile.length(),
                wasLossless = result.wasLossless,
                reviewedAt = System.currentTimeMillis(),
                backupRelativePath = entry.relativePath,
            ),
        )

        localCopy.delete()
        if (result.outputFile != localCopy) result.outputFile.delete()
        return CompressionProcessResult(
            destinationFile = destinationFile,
            originalBytes = entry.sizeBytes,
            compressedBytes = destinationFile.length(),
            isVideo = entry.kind == FileKind.VIDEO,
        )
    }

    private suspend fun processRealFile(
        entry: FileEntry,
        sourceFile: File,
        localCopy: File,
        workDir: File,
        onProgress: (Int) -> Unit,
    ): CompressionProcessResult? {
        // Reuses the same compress → backup/replace logic as SAF, but with real files
        val storageMode = settings.storageMode
        if (storageMode == StorageMode.REPLACE_IN_PLACE) {
            backupRealFile(sourceFile, localCopy)
        }

        val profile = profileFor(entry, localCopy)
        val compressor = compressorFor(entry.kind)
        val result = compressor.compress(localCopy, profile, workDir) { enc ->
            onProgress(CompressionProgressPhases.compressPercent(enc))
        }
        val newName = result.outputFile.name
        val saveTotal = result.outputFile.length().coerceAtLeast(1L)

        val destinationFile = when (storageMode) {
            StorageMode.REPLACE_IN_PLACE -> {
                File(sourceFile.parent ?: return null, newName).apply {
                    copyFileWithProgress(result.outputFile, this) { pct ->
                        onProgress(CompressionProgressPhases.savingPercent(saveTotal * pct / 100, saveTotal))
                    }
                }
            }
            StorageMode.COMPRESSED_COPY -> {
                val compressedDir = resolveRealDestinationDir(sourceFile)
                compressedDir.mkdirs()
                File(compressedDir, newName).apply {
                    copyFileWithProgress(result.outputFile, this) { pct ->
                        onProgress(CompressionProgressPhases.savingPercent(saveTotal * pct / 100, saveTotal))
                    }
                }
            }
        }

        MediaMetadataPreserver.restoreFilesystemTimestamps(localCopy, destinationFile)

        dao.update(
            entry.copy(
                displayName = newName,
                relativePath = destinationFile.absolutePath,
                uri = Uri.fromFile(destinationFile).toString(),
                decision = Decision.DONE,
                compressedSizeBytes = result.outputFile.length(),
                wasLossless = result.wasLossless,
                reviewedAt = System.currentTimeMillis(),
                backupRelativePath = entry.relativePath,
            ),
        )

        localCopy.delete()
        if (result.outputFile != localCopy) result.outputFile.delete()
        return CompressionProcessResult(
            destinationFile = destinationFile,
            originalBytes = entry.sizeBytes,
            compressedBytes = destinationFile.length(),
            isVideo = entry.kind == FileKind.VIDEO,
        )
    }

    // settings.destinationTreeUri (picked via SAF in Settings) is the same destination the
    // SAF pipeline uses via resolveDestinationRoot() — real-file mode must honor it too,
    // rather than silently falling back to a sibling COMPRESSED/ folder next to the source.
    private fun resolveRealDestinationDir(sourceFile: File): File {
        settings.sessionDestinationTreeUri?.let { uriStr ->
            if (hasFullStorageAccess()) {
                resolveRealFile(Uri.parse(uriStr))?.let { return it }
            }
        }
        settings.destinationTreeUri?.let { uriStr ->
            if (hasFullStorageAccess()) {
                resolveRealFile(Uri.parse(uriStr))?.let { return it }
            }
        }
        return File(sourceFile.parent ?: error("source file has no parent"), "COMPRESSED")
    }

    private fun backupRealFile(sourceFile: File, original: File) {
        val originalsDir = File(sourceFile.parent ?: return, FolderScanner.ORIGINALS_DIR_NAME)
        originalsDir.mkdirs()
        val backupFile = File(originalsDir, sourceFile.name)
        original.inputStream().use { input ->
            backupFile.outputStream().use { input.copyTo(it) }
        }
    }

    /** Overwrites [sourceDoc] with the compressed bytes, renaming it first if the extension changed. */
    private fun replaceInPlace(sourceDoc: DocumentFile, newName: String): DocumentFile {
        if (newName == sourceDoc.name) return sourceDoc
        val parent = sourceDoc.parentFile ?: error("source document has no parent")
        sourceDoc.delete()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(newName.substringAfterLast('.', "")) ?: "application/octet-stream"
        return parent.createFile(mime, newName)!!
    }

    private fun resolveDestinationRoot(sourceRoot: DocumentFile): DocumentFile {
        settings.sessionDestinationTreeUri?.let { uri ->
            DocumentFile.fromTreeUri(context, Uri.parse(uri))?.let { return it }
        }
        settings.destinationTreeUri?.let { uri ->
            DocumentFile.fromTreeUri(context, Uri.parse(uri))?.let { return it }
        }
        val name = FolderScanner.DEFAULT_DESTINATION_DIR_NAME
        return sourceRoot.findFile(name) ?: sourceRoot.createDirectory(name)!!
    }

    private fun backupOriginal(root: DocumentFile, relativePath: String, original: File) {
        val originalsRoot = root.findFile(FolderScanner.ORIGINALS_DIR_NAME) ?: root.createDirectory(FolderScanner.ORIGINALS_DIR_NAME)!!
        val backupDoc = ensureMirroredPath(originalsRoot, relativePath)
        context.contentResolver.openOutputStream(backupDoc.uri, "wt")!!.use { out ->
            original.inputStream().use { it.copyTo(out) }
        }
    }

    private fun writeInto(doc: DocumentFile, newContent: File, originalLastModified: Long) {
        context.contentResolver.openOutputStream(doc.uri, "wt")!!.use { out ->
            newContent.inputStream().use { it.copyTo(out) }
        }
        // doc.uri.path is the encoded SAF document path, not a real filesystem path,
        // so this only works (and needs) MANAGE_EXTERNAL_STORAGE to resolve+touch the real file.
        if (hasFullStorageAccess()) {
            runCatching { resolveRealFile(doc.uri)?.setLastModified(originalLastModified) }
        }
    }

    private fun ensureMirroredPath(dirRoot: DocumentFile, relativePath: String): DocumentFile {
        val segments = relativePath.split("/")
        var dir = dirRoot
        for (segment in segments.dropLast(1)) {
            dir = dir.findFile(segment) ?: dir.createDirectory(segment)!!
        }
        val fileName = segments.last()
        return dir.findFile(fileName) ?: dir.createFile("application/octet-stream", fileName)!!
    }

    private fun compressorFor(kind: FileKind): Compressor = when (kind) {
        FileKind.PHOTO, FileKind.LIVE_PHOTO -> PhotoCompressor()
        FileKind.VIDEO -> VideoCompressor(context)
        else -> error("Unsupported for compression: $kind")
    }
}
