package com.example.pdf_reader.pdfium

/** Flags for [PdfTextPage.search]. Values match `FPDF_MATCH*` in fpdf_text.h. */
object SearchFlags {
    const val NONE = 0
    const val MATCH_CASE = 0x1
    const val MATCH_WHOLE_WORD = 0x2
    const val CONSECUTIVE = 0x4
}

/** Flags for [PdfDocument.saveTo]. Values match fpdf_save.h. */
object SaveFlags {
    const val NONE = 0
    const val INCREMENTAL = 1 shl 0
    const val NO_INCREMENTAL = 1 shl 1
    const val REMOVE_SECURITY = 1 shl 2
}

/** Link under a point: jump inside the document or an external URI. */
sealed interface PdfLink {
    data class Page(val pageIndex: Int) : PdfLink
    data class Uri(val uri: String) : PdfLink

    companion object {
        /** Parses the `"P:<index>"` / `"U:<uri>"` format of [com.example.pdf_reader.pdfium.jni.LinkNative]. */
        internal fun parse(raw: String?): PdfLink? = when {
            raw == null -> null
            raw.startsWith("P:") -> raw.substring(2).toIntOrNull()?.let(::Page)
            raw.startsWith("U:") -> raw.substring(2).takeIf { it.isNotBlank() }?.let(::Uri)
            else -> null
        }
    }
}

/** Rectangle in PDF page space: points, origin bottom-left, so [top] > [bottom]. */
data class PdfRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = top - bottom
}

/** Page size in PDF points, rotation applied. */
data class PdfSize(val width: Double, val height: Double)

/** One search hit: char range on the page and its highlight rectangles. */
data class PdfSearchMatch(val charIndex: Int, val charCount: Int, val rects: List<PdfRect>)

/**
 * Rectangle in normalized page space: 0..1 on both axes, origin top-left,
 * exactly like the rendered bitmap. Multiply by the drawn page size to get pixels.
 */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

/** One character of the document. Ordered by page, then char index. */
data class TextPosition(val pageIndex: Int, val charIndex: Int) : Comparable<TextPosition> {
    override fun compareTo(other: TextPosition): Int =
        compareValuesBy(this, other, TextPosition::pageIndex, TextPosition::charIndex)
}

/** Chars [start] until [endExclusive] on one page. */
data class TextRange(val pageIndex: Int, val start: Int, val endExclusive: Int) {
    init {
        require(start in 0..endExclusive) { "Invalid range $start..<$endExclusive" }
    }

    val count: Int get() = endExclusive - start
    val isEmpty: Boolean get() = count == 0
}

/** One document-wide search hit with normalized highlight rectangles. */
data class SearchHit(
    val pageIndex: Int,
    val charIndex: Int,
    val charCount: Int,
    val rects: List<NormRect>,
)

/**
 * Search progress for one scanned page. [hits] may be empty; [pagesScanned]
 * counts pages done so far (including this one) out of [pageCount].
 */
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
