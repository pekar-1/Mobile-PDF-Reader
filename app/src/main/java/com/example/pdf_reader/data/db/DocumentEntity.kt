package com.example.pdf_reader.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val hash: String,
    val uri: String,
    val displayName: String,
    val lastOpenedAt: Long,
    val lastPageIndex: Int,
    val pageCount: Int,
    val thumbnailPath: String?,
    val pinned: Boolean,
)

@Entity(
    tableName = "document_tags",
    primaryKeys = ["hash", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["hash"],
            childColumns = ["hash"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("hash")],
)
data class DocumentTagEntity(
    val hash: String,
    val tag: String,
    val colorArgb: Int,
)

data class DocumentWithTags(
    @Embedded val document: DocumentEntity,
    @Relation(parentColumn = "hash", entityColumn = "hash")
    val tags: List<DocumentTagEntity>,
)
