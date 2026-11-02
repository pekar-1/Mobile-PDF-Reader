package com.example.pdf_reader.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.example.pdf_reader.domain.model.PdfFileInfo
import com.example.pdf_reader.domain.model.PdfPageInfo

interface IPdfEngine {
    suspend fun open(uri: Uri): PdfFileInfo
    suspend fun getPageInfo(pageIndex: Int): PdfPageInfo
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap
    fun close()
}
