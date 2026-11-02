package com.example.pdf_reader.domain.repository

import com.example.pdf_reader.domain.model.PdfFileInfo
import com.example.pdf_reader.domain.model.RecentFile
import kotlinx.coroutines.flow.Flow

interface IFileRepository {
    fun observeRecentFiles(): Flow<List<RecentFile>>
    suspend fun saveOpenedFile(info: PdfFileInfo)
}
