package com.sharma2464.mediacompression.scan

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.sharma2464.mediacompression.data.FileKind
import java.io.File

/** Classifies files into [FileKind] based on MIME type. */
fun classifyFile(mimeType: String): FileKind = when {
    mimeType.startsWith("image/") -> FileKind.PHOTO
    mimeType.startsWith("video/") -> FileKind.VIDEO
    mimeType == "application/pdf" -> FileKind.PDF
    mimeType.startsWith("text/") -> FileKind.TEXT
    mimeType.contains("word") || mimeType.contains("sheet") || mimeType.contains("presentation") -> FileKind.DOCUMENT
    else -> FileKind.OTHER
}

/** Guesses MIME type from filename extension. */
fun guessMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "")
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

/** Cheap bounded sniff for Google Motion Photos (lazy file system path variant). */
fun isMotionPhotoFile(file: File): Boolean = runCatching {
    if (!file.isFile || !file.canRead()) return@runCatching false
    val head = ByteArray(MOTION_PHOTO_SNIFF_BYTES)
    val read = file.inputStream().use { it.read(head) }
    if (read <= 0) return@runCatching false
    val text = String(head, 0, read, Charsets.ISO_8859_1)
    text.contains("MotionPhoto") || text.contains("MicroVideo")
}.getOrDefault(false)

/** Cheap bounded sniff for Google Motion Photos (DocumentFile variant, for SAF scanning). */
fun isMotionPhotoDoc(context: Context, doc: DocumentFile): Boolean = runCatching {
    context.contentResolver.openInputStream(doc.uri)?.use { input ->
        val head = ByteArray(MOTION_PHOTO_SNIFF_BYTES)
        val read = input.read(head)
        if (read <= 0) return@use false
        val text = String(head, 0, read, Charsets.ISO_8859_1)
        text.contains("MotionPhoto") || text.contains("MicroVideo")
    } ?: false
}.getOrDefault(false)

private const val MOTION_PHOTO_SNIFF_BYTES = 64 * 1024
