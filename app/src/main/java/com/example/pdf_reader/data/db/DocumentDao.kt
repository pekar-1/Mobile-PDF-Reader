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

    @Query("DELETE FROM documents WHERE hash = :hash")
    suspend fun deleteDocument(hash: String)

    @Query("UPDATE documents SET lastOpenedAt = :now WHERE hash = :hash")
    suspend fun touchOpened(hash: String, now: Long)

    /** Clamps to [0, pageCount - 1] when the page count is known, otherwise only to >= 0. */
    @Query(
        """
        UPDATE documents SET lastPageIndex =
            CASE WHEN pageCount > 0 THEN MIN(MAX(:pageIndex, 0), pageCount - 1)
                 ELSE MAX(:pageIndex, 0) END
        WHERE hash = :hash
        """
    )
    suspend fun updateLastPage(hash: String, pageIndex: Int)

    @Query("UPDATE documents SET pageCount = :pageCount WHERE hash = :hash")
    suspend fun updatePageCount(hash: String, pageCount: Int)

    @Query("UPDATE documents SET pageCount = :pageCount, thumbnailUpdatedAt = :updatedAt WHERE hash = :hash")
    suspend fun updatePreview(hash: String, pageCount: Int, updatedAt: Long)

    @Query("UPDATE documents SET pinned = :pinned WHERE hash = :hash")
    suspend fun setPinned(hash: String, pinned: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagIfAbsent(tag: TagEntity)

    @Query("UPDATE tags SET colorArgb = :colorArgb WHERE name = :name")
    suspend fun updateTagColor(name: String, colorArgb: Int)

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getTag(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: DocumentTagCrossRef)

    @Query("DELETE FROM document_tags WHERE hash = :hash AND tag = :tag")
    suspend fun deleteCrossRef(hash: String, tag: String)

    @Query("DELETE FROM tags WHERE name NOT IN (SELECT tag FROM document_tags)")
    suspend fun deleteOrphanTags()
}
