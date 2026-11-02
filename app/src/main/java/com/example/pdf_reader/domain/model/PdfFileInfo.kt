package com.example.pdf_reader.domain.model

data class PdfFileInfo(
    val uri: String,
    val displayName: String,
    val pageCount: Int,
)
