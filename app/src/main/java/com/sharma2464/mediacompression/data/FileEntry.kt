package com.sharma2464.mediacompression.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Decision { PENDING, KEEP, COMPRESS, LATER, DONE }

enum class FileKind { PHOTO, VIDEO, LIVE_PHOTO, PDF, DOCUMENT, TEXT, OTHER }

/**
 * One indexed file, sourced from either a SAF tree (legacy) or a real-file browser.
 * [uri] is either a "content://" (SAF) or "file://" URI string and must be unique.
 * [relativePath] is for SAF-scanned files (mirrors tree under "<root name>_BACKUP/");
 * for real-file browser entries, it holds the absolute file path for backup tracking.
 */
@Entity(tableName = "file_entries", indices = [Index(value = ["uri"], unique = true)])
data class FileEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val lastModified: Long,
    val decision: Decision = Decision.PENDING,
    val compressedSizeBytes: Long? = null,
    val wasLossless: Boolean? = null,
    val reviewedAt: Long? = null,
    // relativePath under BACKUP/ as of backup time — needed because [relativePath] itself
    // gets overwritten with the post-compression name (e.g. extension change on rename).
    val backupRelativePath: String? = null,
)
