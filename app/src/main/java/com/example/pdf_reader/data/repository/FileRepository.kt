package com.example.pdf_reader.data.repository

import com.example.pdf_reader.data.local.db.FileDao
import com.example.pdf_reader.data.local.db.FileEntity
import com.example.pdf_reader.domain.model.PdfFileInfo
import com.example.pdf_reader.domain.model.RecentFile
import com.example.pdf_reader.domain.repository.IFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    private val fileDao: FileDao,
) : IFileRepository {

    override fun observeRecentFiles(): Flow<List<RecentFile>> {
        return fileDao.getRecent().map { entities ->
            entities.map { entity ->
                RecentFile(
                    uri = entity.uri,
                    displayName = entity.displayName,
                    pageCount = entity.pageCount,
                    lastOpenedMs = entity.lastOpenedMs,
                )
            }
        }
    }

    override suspend fun saveOpenedFile(info: PdfFileInfo) {
        fileDao.upsert(
            FileEntity(
                uri = info.uri,
                displayName = info.displayName,
                pageCount = info.pageCount,
                lastOpenedMs = System.currentTimeMillis(),
            ),
        )
    }
}
