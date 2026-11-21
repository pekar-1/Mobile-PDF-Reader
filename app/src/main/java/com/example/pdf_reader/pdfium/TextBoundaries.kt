package com.example.pdf_reader.pdfium

// Own word check instead of BreakIterator, which works different on some Android versions.
internal object TextBoundaries {

    // Returns [start, end) of the word, or just the char if it's a space/dot.
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
