package com.sharma2464.tindercompression.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromDecision(value: Decision): String = value.name

    @TypeConverter
    fun toDecision(value: String): Decision = Decision.valueOf(value)

    @TypeConverter
    fun fromKind(value: FileKind): String = value.name

    @TypeConverter
    fun toKind(value: String): FileKind = FileKind.valueOf(value)
}

@Database(entities = [FileEntry::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileEntryDao(): FileEntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tinder-compression.db",
            )
                // ponytail: solo-dev tool, re-scanning is cheap — destructive migration
                // instead of a real migration path for schema bumps.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
