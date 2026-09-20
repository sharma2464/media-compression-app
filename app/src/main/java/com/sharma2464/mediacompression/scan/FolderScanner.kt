package com.sharma2464.mediacompression.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind

enum class ScanResult { SUCCESS, REJECTED_BACKUP_FOLDER, ROOT_UNREADABLE }

/** Walks a SAF tree and indexes every file into Room. Skips anything under its own backup dir. */
class FolderScanner(private val context: Context) {

    suspend fun scan(rootTreeUri: Uri): ScanResult {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return ScanResult.ROOT_UNREADABLE
        if (isBackupFolder(root)) return ScanResult.REJECTED_BACKUP_FOLDER

        val enabledKinds = AppSettings(context).enabledKinds
        val found = mutableListOf<FileEntry>()
        walk(root, relativePath = "", found, backupDirName(root), enabledKinds)
        AppDatabase.get(context).fileEntryDao().insertAll(found)
        return ScanResult.SUCCESS
    }

    /**
     * A folder is off-limits as a review root if it *is* a backup folder, or (when we can
     * check real ancestor names, i.e. full storage access is granted) sits inside one —
     * reviewing a backup folder would re-index and recompress its own originals, spawning
     * a nested backup-of-a-backup on every run.
     */
    private fun isBackupFolder(root: DocumentFile): Boolean {
        val name = root.name ?: return false
        if (name.endsWith(BACKUP_SUFFIX) || name == LEGACY_BACKUP_DIR_NAME || name == ORIGINALS_DIR_NAME) return true
        if (!hasFullStorageAccess()) return false
        val real = resolveRealFile(root.uri) ?: return false
        return generateSequence(real) { it.parentFile }
            .any { it.name.endsWith(BACKUP_SUFFIX) || it.name == LEGACY_BACKUP_DIR_NAME || it.name == ORIGINALS_DIR_NAME }
    }

    private fun walk(
        dir: DocumentFile,
        relativePath: String,
        out: MutableList<FileEntry>,
        backupDirName: String,
        enabledKinds: Set<FileKind>,
    ) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            // never index our own backups or default compressed-output destination
            if (relativePath.isEmpty() &&
                (name == backupDirName || name == LEGACY_BACKUP_DIR_NAME || name == ORIGINALS_DIR_NAME || name == DEFAULT_DESTINATION_DIR_NAME)
            ) {
                continue
            }
            val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (child.isDirectory) {
                walk(child, childPath, out, backupDirName, enabledKinds)
            } else {
                val mime = child.type ?: guessMime(name)
                val baseKind = classify(mime)
                val kind = if (baseKind == FileKind.PHOTO && isMotionPhoto(child)) FileKind.LIVE_PHOTO else baseKind
                if (kind !in enabledKinds) continue
                out += FileEntry(
                    uri = child.uri.toString(),
                    relativePath = childPath,
                    displayName = name,
                    mimeType = mime,
                    kind = kind,
                    sizeBytes = child.length(),
                    lastModified = child.lastModified(),
                )
            }
        }
    }

    private fun guessMime(name: String): String = guessMimeType(name)

    private fun isMotionPhoto(doc: DocumentFile): Boolean = isMotionPhotoDoc(context, doc)

    private fun classify(mime: String): FileKind = classifyFile(mime)

    companion object {
        const val BACKUP_SUFFIX = "_BACKUP"
        const val DEFAULT_DESTINATION_DIR_NAME = "COMPRESSED"
        const val ORIGINALS_DIR_NAME = "ORIGINALS"
        private const val LEGACY_BACKUP_DIR_NAME = "BACKUP"

        /** Legacy naming (pre-[ORIGINALS_DIR_NAME]) — still skipped on scan so old backups aren't reviewed as content. */
        fun backupDirName(root: DocumentFile): String = "${root.name}$BACKUP_SUFFIX"
    }
}
