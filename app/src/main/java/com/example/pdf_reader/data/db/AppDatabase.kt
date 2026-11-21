package com.example.pdf_reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, TagEntity::class, DocumentTagCrossRef::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        const val NAME = "pdf_reader.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                )
                    // Pre-release dev builds shipped incompatible schemas as versions 1 and 2 without
                    // migrations, so wipe those. From 3 on, upgrades must ship a Migration/AutoMigration.
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
