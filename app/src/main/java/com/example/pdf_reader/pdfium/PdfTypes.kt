package com.example.pdf_reader.pdfium

// Same values as FPDF_MATCH* in fpdf_text.h
object SearchFlags {
    const val NONE = 0
    const val MATCH_CASE = 0x1
    const val MATCH_WHOLE_WORD = 0x2
    const val CONSECUTIVE = 0x4
}

// Same values as in fpdf_save.h
object SaveFlags {
    const val NONE = 0
    const val INCREMENTAL = 1 shl 0
    const val NO_INCREMENTAL = 1 shl 1
    const val REMOVE_SECURITY = 1 shl 2
}

sealed interface PdfLink {
    data class Page(val pageIndex: Int) : PdfLink
    data class Uri(val uri: String) : PdfLink

    companion object {
        // Native code returns "P:<page>" or "U:<url>".
        internal fun parse(raw: String?): PdfLink? = when {
            raw == null -> null
            raw.startsWith("P:") -> raw.substring(2).toIntOrNull()?.let(::Page)
            raw.startsWith("U:") -> raw.substring(2).takeIf { it.isNotBlank() }?.let(::Uri)
            else -> null
        }
    }
}

// In PDF points, origin is bottom-left so top > bottom.
data class PdfRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = top - bottom
}

data class PdfSize(val width: Double, val height: Double)

data class PdfSearchMatch(val charIndex: Int, val charCount: Int, val rects: List<PdfRect>)

// 0..1 on both axes, origin top-left. Multiply by page size to get pixels.
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

data class TextPosition(val pageIndex: Int, val charIndex: Int) : Comparable<TextPosition> {
    override fun compareTo(other: TextPosition): Int =
        compareValuesBy(this, other, TextPosition::pageIndex, TextPosition::charIndex)
}

data class TextRange(val pageIndex: Int, val start: Int, val endExclusive: Int) {
    init {
        require(start in 0..endExclusive) { "Invalid range $start..<$endExclusive" }
    }

    val count: Int get() = endExclusive - start
    val isEmpty: Boolean get() = count == 0
}

data class SearchHit(
    val pageIndex: Int,
    val charIndex: Int,
    val charCount: Int,
    val rects: List<NormRect>,
)

data class PageSearchResult(
    val pageIndex: Int,
    val hits: List<SearchHit>,
    val pagesScanned: Int,
    val pageCount: Int,
)

internal fun List<PdfRect>.toFlatArray(): DoubleArray {
    val values = DoubleArray(size * 4)
    forEachIndexed { i, r ->
        values[i * 4] = r.left
        values[i * 4 + 1] = r.top
        values[i * 4 + 2] = r.right
        values[i * 4 + 3] = r.bottom
    }
    return values
}

internal fun DoubleArray.toNormRects(): List<NormRect> =
    (0 until size / 4).map { i ->
        NormRect(
            this[i * 4].toFloat(), this[i * 4 + 1].toFloat(),
            this[i * 4 + 2].toFloat(), this[i * 4 + 3].toFloat(),
        )
    }

internal fun DoubleArray.toRect() = PdfRect(this[0], this[1], this[2], this[3])

internal fun DoubleArray.toRects(): List<PdfRect> =
    (0 until size / 4).map { i ->
        PdfRect(this[i * 4], this[i * 4 + 1], this[i * 4 + 2], this[i * 4 + 3])
    }
