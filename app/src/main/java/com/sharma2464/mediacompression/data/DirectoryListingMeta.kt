package com.sharma2464.mediacompression.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "directory_listing_meta")
data class DirectoryListingMeta(
    @PrimaryKey val parentPath: String,
    val listedAt: Long,
    val parentLastModified: Long,
)
