package com.example.pdf_reader.pdfium

/**
 * Word boundaries in page text. A word is a run of letters, digits, `_` and
 * apostrophes, so results are the same on the JVM and on every Android
 * version (BreakIterator implementations differ, e.g. on hyphens).
 * Indices are PDFium char indices, which match UTF-16 indices of
 * [PdfTextPage.text] for BMP text.
 */
internal object TextBoundaries {

    /**
     * Range `[start, end)` of the word containing [index]. When the char at
     * [index] is not part of a word (whitespace, punctuation), returns just
     * that char. Returns `null` when [index] is out of range.
     */
    fun wordAround(text: String, index: Int): Pair<Int, Int>? {
        if (index !in text.indices) return null
        if (!text[index].isWordChar()) return index to index + 1
        var start = index
        while (start > 0 && text[start - 1].isWordChar()) start--
        var end = index + 1
        while (end < text.length && text[end].isWordChar()) end++
        return start to end
    }

    private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_' || this == '\''
}
