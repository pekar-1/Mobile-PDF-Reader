package com.example.pdf_reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Transaction
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<DocumentWithTags>>

    @Query("SELECT * FROM documents WHERE hash = :hash")
    suspend fun get(hash: String): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("UPDATE documents SET lastPageIndex = :pageIndex WHERE hash = :hash")
    suspend fun updateLastPage(hash: String, pageIndex: Int)

    @Query("UPDATE documents SET pageCount = :pageCount WHERE hash = :hash")
    suspend fun updatePageCount(hash: String, pageCount: Int)

    @Query("UPDATE documents SET pageCount = :pageCount, thumbnailPath = :thumbnailPath WHERE hash = :hash")
    suspend fun updatePreview(hash: String, pageCount: Int, thumbnailPath: String)

    @Query("UPDATE documents SET pinned = :pinned WHERE hash = :hash")
    suspend fun setPinned(hash: String, pinned: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: DocumentTagEntity)

    @Query("DELETE FROM document_tags WHERE hash = :hash AND tag = :tag")
    suspend fun deleteTag(hash: String, tag: String)
}
