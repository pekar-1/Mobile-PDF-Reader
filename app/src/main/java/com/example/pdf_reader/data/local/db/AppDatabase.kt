package com.example.pdf_reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
}
