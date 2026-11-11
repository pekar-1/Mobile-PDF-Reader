package com.example.pdf_reader.ui.pdf.selection

import com.example.pdf_reader.pdfium.TextPosition
import com.example.pdf_reader.pdfium.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionMathTest {

    private fun pos(page: Int, char: Int) = TextPosition(page, char)

    @Test
    fun rangesOnOnePage() {
        assertEquals(listOf(TextRange(2, 5, 10)), SelectionMath.ranges(pos(2, 5), pos(2, 9)) { 100 })
    }

    @Test
    fun rangesAcrossPages() {
        val ranges = SelectionMath.ranges(pos(1, 90), pos(3, 4)) { page -> if (page == 2) 50 else 100 }
        assertEquals(listOf(TextRange(1, 90, 100), TextRange(2, 0, 50), TextRange(3, 0, 5)), ranges)
    }

    @Test
    fun rangesSkipEmptyPages() {
        // Pages 0 and 1 have no text left to select; only page 2 contributes.
        val ranges = SelectionMath.ranges(pos(0, 3), pos(2, 0)) { 0 }
        assertEquals(listOf(TextRange(2, 0, 1)), ranges)
    }

    @Test
    fun reversedRangeIsEmpty() {
        assertEquals(emptyList<TextRange>(), SelectionMath.ranges(pos(1, 5), pos(1, 2)) { 10 })
    }

    @Test
    fun extendKeepsFixedWord() {
        val word = pos(0, 10) to pos(0, 14)
        assertEquals(pos(0, 10) to pos(0, 30), SelectionMath.extend(word, pos(0, 30)))
        assertEquals(pos(0, 2) to pos(0, 14), SelectionMath.extend(word, pos(0, 2)))
        assertEquals(word, SelectionMath.extend(word, pos(0, 12)))
        assertEquals(pos(0, 10) to pos(2, 1), SelectionMath.extend(word, pos(2, 1)))
    }

    @Test
    fun handleCrossingFlipsDirection() {
        // Dragging the end handle (fixed = start) before the start.
        val fixedStart = pos(1, 20) to pos(1, 20)
        assertEquals(pos(1, 5) to pos(1, 20), SelectionMath.extend(fixedStart, pos(1, 5)))
    }

    @Test
    fun boundsOfRange() {
        assertEquals(pos(4, 3) to pos(4, 7), SelectionMath.bounds(TextRange(4, 3, 8)))
    }
}
