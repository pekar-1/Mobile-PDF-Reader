package com.example.pdf_reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FileEntity)

    @Query("SELECT * FROM files ORDER BY lastOpenedMs DESC")
    fun getRecent(): Flow<List<FileEntity>>
}
