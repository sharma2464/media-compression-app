package com.sharma2464.tindercompression.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Decision { PENDING, KEEP, COMPRESS, LATER, DONE }

enum class FileKind { PHOTO, VIDEO, LIVE_PHOTO, PDF, DOCUMENT, TEXT, OTHER }

/**
 * One indexed file under the user's chosen SAF root.
 * [uri] is the SAF document URI string; [relativePath] mirrors the source tree under BACKUP/.
 */
@Entity(tableName = "file_entries")
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
)
