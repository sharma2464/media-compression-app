package com.sharma2464.mediacompression.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface BrowserCacheDao {
    @Query("SELECT * FROM cached_browser_entries WHERE parentPath = :parentPath")
    suspend fun getChildren(parentPath: String): List<CachedBrowserEntry>

    @Query("SELECT * FROM directory_listing_meta WHERE parentPath = :parentPath LIMIT 1")
    suspend fun getMeta(parentPath: String): DirectoryListingMeta?

    @Transaction
    suspend fun replaceChildren(
        parentPath: String,
        children: List<CachedBrowserEntry>,
        meta: DirectoryListingMeta,
    ) {
        deleteChildren(parentPath)
        if (children.isNotEmpty()) {
            insertAll(children)
        }
        upsertMeta(meta)
    }

    @Query("DELETE FROM cached_browser_entries WHERE parentPath = :parentPath")
    suspend fun deleteChildren(parentPath: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(children: List<CachedBrowserEntry>)

    @Upsert
    suspend fun upsertMeta(meta: DirectoryListingMeta)
}
