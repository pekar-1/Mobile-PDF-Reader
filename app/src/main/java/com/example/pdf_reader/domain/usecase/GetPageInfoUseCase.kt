package com.example.pdf_reader.domain.usecase

import com.example.pdf_reader.domain.model.PdfPageInfo
import com.example.pdf_reader.domain.repository.IPdfEngine
import javax.inject.Inject

class GetPageInfoUseCase @Inject constructor(
    private val pdfEngine: IPdfEngine,
) {
    suspend operator fun invoke(pageIndex: Int): PdfPageInfo {
        return pdfEngine.getPageInfo(pageIndex)
    }
}
