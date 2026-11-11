package com.example.pdf_reader.ui.pdf.selection

import com.example.pdf_reader.pdfium.TextPosition
import com.example.pdf_reader.pdfium.TextRange

/** Pure selection logic, free of Compose and PDFium so it is unit-testable on the JVM. */
object SelectionMath {

    /**
     * Per-page ranges covering [start]..[endInclusive] (document order).
     * [charCount] gives the number of chars of pages strictly between them.
     */
    fun ranges(start: TextPosition, endInclusive: TextPosition, charCount: (Int) -> Int): List<TextRange> {
        if (endInclusive < start) return emptyList()
        return (start.pageIndex..endInclusive.pageIndex).mapNotNull { page ->
            val from = if (page == start.pageIndex) start.charIndex else 0
            val to = if (page == endInclusive.pageIndex) endInclusive.charIndex + 1 else charCount(page)
            if (to > from) TextRange(page, from, to) else null
        }
    }

    /**
     * Selection while dragging from a [fixed] range (the word of a long press,
     * or the opposite handle) to [target]: it always keeps [fixed] and grows
     * towards [target], so crossing over just flips the direction.
     * Returns `(start, endInclusive)`.
     */
    fun extend(fixed: Pair<TextPosition, TextPosition>, target: TextPosition): Pair<TextPosition, TextPosition> {
        val (fixedStart, fixedEnd) = fixed
        return when {
            target < fixedStart -> target to fixedEnd
            target > fixedEnd -> fixedStart to target
            else -> fixedStart to fixedEnd
        }
    }

    /** Inclusive positions of a non-empty [range]. */
    fun bounds(range: TextRange): Pair<TextPosition, TextPosition> =
        TextPosition(range.pageIndex, range.start) to TextPosition(range.pageIndex, range.endExclusive - 1)
}
