package com.example.pdf_reader.domain.usecase

import android.graphics.Bitmap
import com.example.pdf_reader.domain.repository.IPdfEngine
import javax.inject.Inject

class RenderPageUseCase @Inject constructor(
    private val pdfEngine: IPdfEngine,
) {
    suspend fun render(pageIndex: Int, targetWidthPx: Int): Bitmap {
        return pdfEngine.renderPage(pageIndex, targetWidthPx)
    }
}
