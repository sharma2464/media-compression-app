package com.sharma2464.mediacompression.scan

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind

enum class ScanResult { SUCCESS, REJECTED_BACKUP_FOLDER, ROOT_UNREADABLE }

/** Walks a SAF tree and indexes every file into Room. Skips anything under its own backup dir. */
class FolderScanner(private val context: Context) {

    suspend fun scan(rootTreeUri: Uri): ScanResult {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return ScanResult.ROOT_UNREADABLE
        if (isBackupFolder(root)) return ScanResult.REJECTED_BACKUP_FOLDER

        val found = mutableListOf<FileEntry>()
        walk(root, relativePath = "", found, backupDirName(root))
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
        if (name.endsWith(BACKUP_SUFFIX) || name == LEGACY_BACKUP_DIR_NAME) return true
        if (!hasFullStorageAccess()) return false
        val real = resolveRealFile(root.uri) ?: return false
        return generateSequence(real) { it.parentFile }
            .any { it.name.endsWith(BACKUP_SUFFIX) || it.name == LEGACY_BACKUP_DIR_NAME }
    }

    private fun walk(dir: DocumentFile, relativePath: String, out: MutableList<FileEntry>, backupDirName: String) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            if (relativePath.isEmpty() && (name == backupDirName || name == LEGACY_BACKUP_DIR_NAME)) continue // never index our own backups
            val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (child.isDirectory) {
                walk(child, childPath, out, backupDirName)
            } else {
                val mime = child.type ?: guessMime(name)
                val baseKind = classify(mime)
                val kind = if (baseKind == FileKind.PHOTO && isMotionPhoto(child)) FileKind.LIVE_PHOTO else baseKind
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

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /**
     * Cheap bounded sniff for Google Motion Photos: the XMP block with the
     * `MotionPhoto`/`MicroVideoOffset` marker lives in the first few KB of the JPEG.
     * ponytail: Apple Live Photo detection (matching `content.identifier` between a
     * photo and a sibling .MOV) isn't wired in yet — tracked as a follow-up, since it
     * needs parsing the QuickTime atom, not just a byte scan.
     */
    private fun isMotionPhoto(doc: DocumentFile): Boolean = runCatching {
        context.contentResolver.openInputStream(doc.uri)?.use { input ->
            val head = ByteArray(MOTION_PHOTO_SNIFF_BYTES)
            val read = input.read(head)
            if (read <= 0) return@use false
            val text = String(head, 0, read, Charsets.ISO_8859_1)
            text.contains("MotionPhoto") || text.contains("MicroVideo")
        } ?: false
    }.getOrDefault(false)

    private fun classify(mime: String): FileKind = when {
        mime.startsWith("image/") -> FileKind.PHOTO
        mime.startsWith("video/") -> FileKind.VIDEO
        mime == "application/pdf" -> FileKind.PDF
        mime.startsWith("text/") -> FileKind.TEXT
        mime.contains("word") || mime.contains("sheet") || mime.contains("presentation") -> FileKind.DOCUMENT
        else -> FileKind.OTHER
    }

    companion object {
        const val BACKUP_SUFFIX = "_BACKUP"
        private const val LEGACY_BACKUP_DIR_NAME = "BACKUP"
        private const val MOTION_PHOTO_SNIFF_BYTES = 64 * 1024

        /** e.g. picking "DCIM" backs up under "DCIM_BACKUP" alongside it. */
        fun backupDirName(root: DocumentFile): String = "${root.name}$BACKUP_SUFFIX"
    }
}
