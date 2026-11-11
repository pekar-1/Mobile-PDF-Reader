package com.example.pdf_reader.pdfium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextBoundariesTest {

    private val text = "Hello PDFium, żółw page-one"

    @Test
    fun selectsWholeWordFromAnyChar() {
        assertEquals(6 to 12, TextBoundaries.wordAround(text, 6))
        assertEquals(6 to 12, TextBoundaries.wordAround(text, 9))
        assertEquals(6 to 12, TextBoundaries.wordAround(text, 11))
    }

    @Test
    fun handlesPolishLetters() {
        assertEquals(14 to 18, TextBoundaries.wordAround(text, 15))
    }

    @Test
    fun nonWordCharSelectsItself() {
        assertEquals(12 to 13, TextBoundaries.wordAround(text, 12)) // ','
        assertEquals(5 to 6, TextBoundaries.wordAround(text, 5)) // ' '
    }

    @Test
    fun hyphenSplitsWords() {
        assertEquals(19 to 23, TextBoundaries.wordAround(text, 20))
        assertEquals(24 to 27, TextBoundaries.wordAround(text, 25))
    }

    @Test
    fun outOfRangeIsNull() {
        assertNull(TextBoundaries.wordAround(text, -1))
        assertNull(TextBoundaries.wordAround(text, text.length))
    }
}
