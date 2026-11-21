package com.example.pdf_reader.pdfium

import com.example.pdf_reader.pdfium.jni.SearchNative

class PdfTextSearch internal constructor(
    private val textPage: PdfTextPage,
    private var handle: Long,
) : AutoCloseable {

    fun next(): PdfSearchMatch? =
        if (SearchNative.nativeFindNext(requireHandle())) current() else null

    private fun current(): PdfSearchMatch {
        val index = SearchNative.nativeGetSchResultIndex(handle)
        val count = SearchNative.nativeGetSchCount(handle)
        return PdfSearchMatch(index, count, textPage.rects(index, count))
    }

    override fun close() {
        if (handle == 0L) return
        SearchNative.nativeFindClose(handle)
        handle = 0L
        textPage.onSearchClosed(this)
    }

    private fun requireHandle(): Long {
        check(handle != 0L) { "Search is closed" }
        return handle
    }
}
