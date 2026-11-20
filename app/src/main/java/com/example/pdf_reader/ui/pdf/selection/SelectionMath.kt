package com.example.pdf_reader.ui.pdf.selection

import com.example.pdf_reader.pdfium.TextPosition
import com.example.pdf_reader.pdfium.TextRange

// No Compose / PDFium here, so it can be tested on JVM.
object SelectionMath {

    fun ranges(start: TextPosition, endInclusive: TextPosition, charCount: (Int) -> Int): List<TextRange> {
        if (endInclusive < start) return emptyList()
        return (start.pageIndex..endInclusive.pageIndex).mapNotNull { page ->
            val from = if (page == start.pageIndex) start.charIndex else 0
            val to = if (page == endInclusive.pageIndex) endInclusive.charIndex + 1 else charCount(page)
            if (to > from) TextRange(page, from, to) else null
        }
    }

    // Keeps the fixed part (long-pressed word or other handle) and grows to target.
    fun extend(fixed: Pair<TextPosition, TextPosition>, target: TextPosition): Pair<TextPosition, TextPosition> {
        val (fixedStart, fixedEnd) = fixed
        return when {
            target < fixedStart -> target to fixedEnd
            target > fixedEnd -> fixedStart to target
            else -> fixedStart to fixedEnd
        }
    }

    fun bounds(range: TextRange): Pair<TextPosition, TextPosition> =
        TextPosition(range.pageIndex, range.start) to TextPosition(range.pageIndex, range.endExclusive - 1)
}
