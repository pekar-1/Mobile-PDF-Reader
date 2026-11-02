package com.example.pdf_reader.domain.usecase

import android.net.Uri
import com.example.pdf_reader.data.local.permissions.FilePermissionsManager
import com.example.pdf_reader.domain.model.PdfFileInfo
import com.example.pdf_reader.domain.repository.IFileRepository
import com.example.pdf_reader.domain.repository.IPdfEngine
import javax.inject.Inject

class OpenPdfUseCase @Inject constructor(
    private val pdfEngine: IPdfEngine,
    private val fileRepository: IFileRepository,
    private val filePermissionsManager: FilePermissionsManager,
) {
    suspend operator fun invoke(uri: Uri): PdfFileInfo {
        filePermissionsManager.takePersistableReadPermission(uri)
        val info = pdfEngine.open(uri)
        fileRepository.saveOpenedFile(info)
        return info
    }
}
