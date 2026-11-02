package com.example.pdf_reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val pageCount: Int,
    val lastOpenedMs: Long,
    val lastPageIndex: Int = 0,
    val fileHash: String? = null,
)
