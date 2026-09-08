package com.sharma2464.tindercompression.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileEntryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<FileEntry>)

    @Update
    suspend fun update(entry: FileEntry)

    // PENDING entries have no reviewedAt (sorts first); LATER entries are stamped with
    // reviewedAt when swiped up, so they sort behind everything reviewed before them —
    // i.e. "put back at the end of the queue" rather than immediately resurfacing.
    @Query("SELECT * FROM file_entries WHERE decision = 'PENDING' OR decision = 'LATER' ORDER BY COALESCE(reviewedAt, 0), id LIMIT 1")
    suspend fun nextForReview(): FileEntry?

    @Query("SELECT * FROM file_entries WHERE decision = 'COMPRESS'")
    suspend fun queuedForCompression(): List<FileEntry>

    @Query("SELECT * FROM file_entries ORDER BY id")
    fun observeAll(): Flow<List<FileEntry>>

    @Query("SELECT COUNT(*) FROM file_entries WHERE decision = 'PENDING' OR decision = 'LATER'")
    fun observePendingCount(): Flow<Int>
}
