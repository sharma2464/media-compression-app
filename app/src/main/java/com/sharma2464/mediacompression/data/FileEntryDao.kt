package com.sharma2464.mediacompression.data

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

    @Query("SELECT * FROM file_entries WHERE decision = 'COMPRESS'")
    suspend fun queuedForCompression(): List<FileEntry>

    @Query("SELECT * FROM file_entries ORDER BY id")
    fun observeAll(): Flow<List<FileEntry>>
}
