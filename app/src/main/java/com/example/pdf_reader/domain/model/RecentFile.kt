package com.example.pdf_reader.domain.model

data class RecentFile(
    val uri: String,
    val displayName: String,
    val pageCount: Int,
    val lastOpenedMs: Long,
)
