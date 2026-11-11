package com.example.pdf_reader.ui.pdf.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.pdf_reader.pdfium.NormRect

/**
 * Draws on top of one page (search hits, selection, later annotations).
 * Runs in the draw phase: Compose state read here only redraws the page,
 * without recomposition or relayout. The draw scope size is the page size.
 */
fun interface PageOverlay {
    fun DrawScope.drawOverlay(pageIndex: Int)
}

/** Fills a normalized rect scaled to the page size. */
fun DrawScope.fillNormRect(rect: NormRect, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(rect.left * size.width, rect.top * size.height),
        size = Size(rect.width * size.width, rect.height * size.height),
    )
}
