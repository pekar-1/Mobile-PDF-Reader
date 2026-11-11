package com.example.pdf_reader.pdfium

import com.example.pdf_reader.pdfium.jni.SearchNative
import com.example.pdf_reader.pdfium.jni.TextNative

/** Text layer of a page (FPDF_TEXTPAGE). Closing it closes its searches. */
class PdfTextPage internal constructor(
    private val page: PdfPage,
    private var handle: Long,
) : AutoCloseable {

    private val openSearches = mutableListOf<PdfTextSearch>()

    val isClosed: Boolean get() = handle == 0L

    val charCount: Int get() = TextNative.nativeCountChars(requireHandle())

    /** Text of [count] chars from [startIndex] (FPDFText_GetText); defaults to the whole page. */
    fun text(startIndex: Int = 0, count: Int = charCount - startIndex): String =
        TextNative.nativeGetText(requireHandle(), startIndex, count)

    /** Bounding box of one char (FPDFText_GetCharBox), or `null` for an invalid index. */
    fun charBox(index: Int): PdfRect? =
        TextNative.nativeGetCharBox(requireHandle(), index)?.toRect()

    /** Box with full font ascent/descent (FPDFText_GetLooseCharBox), or `null` for an invalid index. */
    fun looseCharBox(index: Int): PdfRect? =
        TextNative.nativeGetLooseCharBox(requireHandle(), index)?.toRect()

    /**
     * Char index at or near page point ([x], [y]) within the tolerances, in
     * points (FPDFText_GetCharIndexAtPos), or `null` when nothing is near.
     */
    fun charIndexAt(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int? =
        TextNative.nativeGetCharIndexAtPos(requireHandle(), x, y, xTolerance, yTolerance)
            .takeIf { it >= 0 }

    /** Rectangles covering a char range (FPDFText_GetRect). [count] -1 = to the end. */
    fun rects(startIndex: Int, count: Int): List<PdfRect> =
        TextNative.nativeGetRects(requireHandle(), startIndex, count).toRects()

    /**
     * Starts a search (FPDFText_FindStart). [flags]: see [SearchFlags].
     * [startIndex] -1 starts at the end of the page; use [PdfTextSearch.prev] then.
     */
    fun search(query: String, flags: Int = SearchFlags.NONE, startIndex: Int = 0): PdfTextSearch {
        val searchHandle = SearchNative.nativeFindStart(requireHandle(), query, flags, startIndex)
        return PdfTextSearch(this, searchHandle).also { openSearches += it }
    }

    /** All matches on the page, in reading order. */
    fun findAll(query: String, flags: Int = SearchFlags.NONE): List<PdfSearchMatch> =
        search(query, flags).use { search -> generateSequence { search.next() }.toList() }

    override fun close() {
        if (handle == 0L) return
        openSearches.toList().forEach { it.close() }
        TextNative.nativeCloseTextPage(handle)
        handle = 0L
        page.onTextPageClosed(this)
    }

    internal fun requireHandle(): Long {
        check(handle != 0L) { "Text page is closed" }
        return handle
    }

    internal fun onSearchClosed(search: PdfTextSearch) {
        openSearches.remove(search)
    }
}
