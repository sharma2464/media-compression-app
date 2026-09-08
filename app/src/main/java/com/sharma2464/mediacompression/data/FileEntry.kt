package com.sharma2464.mediacompression.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Decision { PENDING, KEEP, COMPRESS, LATER, DONE }

enum class FileKind { PHOTO, VIDEO, LIVE_PHOTO, PDF, DOCUMENT, TEXT, OTHER }

/**
 * One indexed file under the user's chosen SAF root.
 * [uri] is the SAF document URI string; [relativePath] mirrors the source tree under
 * "<root name>_BACKUP/". The unique index on [relativePath] is what makes rescanning a
 * folder idempotent — a file already indexed (in any decision state, including DONE)
 * never gets a second row, so it's never reviewed or compressed twice.
 */
@Entity(tableName = "file_entries", indices = [Index(value = ["relativePath"], unique = true)])
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
