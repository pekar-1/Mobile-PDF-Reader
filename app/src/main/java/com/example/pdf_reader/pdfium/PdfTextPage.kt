package com.example.pdf_reader.pdfium

import com.example.pdf_reader.pdfium.jni.SearchNative
import com.example.pdf_reader.pdfium.jni.TextNative

class PdfTextPage internal constructor(
    private val page: PdfPage,
    private var handle: Long,
) : AutoCloseable {

    private val openSearches = mutableListOf<PdfTextSearch>()

    val isClosed: Boolean get() = handle == 0L

    val charCount: Int get() = TextNative.nativeCountChars(requireHandle())

    fun text(startIndex: Int = 0, count: Int = charCount - startIndex): String =
        TextNative.nativeGetText(requireHandle(), startIndex, count)

    fun charBox(index: Int): PdfRect? =
        TextNative.nativeGetCharBox(requireHandle(), index)?.toRect()

    // Taller box (full font height), same for the whole line.
    fun looseCharBox(index: Int): PdfRect? =
        TextNative.nativeGetLooseCharBox(requireHandle(), index)?.toRect()

    fun charIndexAt(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int? =
        TextNative.nativeGetCharIndexAtPos(requireHandle(), x, y, xTolerance, yTolerance)
            .takeIf { it >= 0 }

    fun rects(startIndex: Int, count: Int): List<PdfRect> =
        TextNative.nativeGetRects(requireHandle(), startIndex, count).toRects()

    fun search(query: String, flags: Int = SearchFlags.NONE): PdfTextSearch {
        val searchHandle = SearchNative.nativeFindStart(requireHandle(), query, flags, 0)
        return PdfTextSearch(this, searchHandle).also { openSearches += it }
    }

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
