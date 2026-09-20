package com.sharma2464.mediacompression.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One child file or folder under [parentPath], persisted for instant browser listings. */
@Entity(
    tableName = "cached_browser_entries",
    indices = [Index(value = ["parentPath"])],
)
data class CachedBrowserEntry(
    @PrimaryKey val absolutePath: String,
    val parentPath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val dirTotalSizeBytes: Long,
    val dirFileCount: Int,
    /** [FileKind.name] for files; null for directories. */
    val kind: String?,
    val dateTakenMs: Long?,
    val listedAt: Long,
)
