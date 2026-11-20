package com.example.pdf_reader.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
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
    // null = no thumbnail yet
    val thumbnailUpdatedAt: Long?,
    val pinned: Boolean,
)

// "Exam" and "exam" are the same tag.
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val colorArgb: Int,
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
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tag"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("hash"), Index("tag")],
)
data class DocumentTagCrossRef(
    val hash: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val tag: String,
)

data class DocumentWithTags(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "hash",
        entityColumn = "name",
        associateBy = Junction(
            value = DocumentTagCrossRef::class,
            parentColumn = "hash",
            entityColumn = "tag",
        ),
    )
    val tags: List<TagEntity>,
)
