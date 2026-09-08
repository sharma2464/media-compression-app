package com.sharma2464.tindercompression.scan

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.sharma2464.tindercompression.data.AppDatabase
import com.sharma2464.tindercompression.data.FileEntry
import com.sharma2464.tindercompression.data.FileKind

/** Walks a SAF tree and indexes every file into Room. Skips anything already under BACKUP/. */
class FolderScanner(private val context: Context) {

    suspend fun scan(rootTreeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return
        val found = mutableListOf<FileEntry>()
        walk(root, relativePath = "", found)
        AppDatabase.get(context).fileEntryDao().insertAll(found)
    }

    private fun walk(dir: DocumentFile, relativePath: String, out: MutableList<FileEntry>) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            if (relativePath.isEmpty() && name == BACKUP_DIR_NAME) continue // never index our own backups
            val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (child.isDirectory) {
                walk(child, childPath, out)
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
        const val BACKUP_DIR_NAME = "BACKUP"
        private const val MOTION_PHOTO_SNIFF_BYTES = 64 * 1024
    }
}
