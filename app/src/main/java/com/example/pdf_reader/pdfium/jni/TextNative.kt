package com.example.pdf_reader.pdfium.jni

/**
 * Bindings for `cpp/text/text_page_jni.cpp`.
 * Rect arrays are `[left, top, right, bottom]` in PDF points, origin bottom-left.
 */
internal object TextNative {
    @JvmStatic external fun nativeLoadTextPage(pageHandle: Long): Long
    @JvmStatic external fun nativeCloseTextPage(textPageHandle: Long)
    @JvmStatic external fun nativeCountChars(textPageHandle: Long): Int
    @JvmStatic external fun nativeGetText(textPageHandle: Long, startIndex: Int, count: Int): String
    @JvmStatic external fun nativeGetCharBox(textPageHandle: Long, index: Int): DoubleArray?

    /** Box with full font ascent/descent (FPDFText_GetLooseCharBox). */
    @JvmStatic external fun nativeGetLooseCharBox(textPageHandle: Long, index: Int): DoubleArray?

    /** Char index near page point (x, y); -1 = nothing near, -3 = error. */
    @JvmStatic external fun nativeGetCharIndexAtPos(
        textPageHandle: Long,
        x: Double,
        y: Double,
        xTolerance: Double,
        yTolerance: Double,
    ): Int

    /** Must precede [nativeGetRect]. */
    @JvmStatic external fun nativeCountRects(textPageHandle: Long, startIndex: Int, count: Int): Int
    @JvmStatic external fun nativeGetRect(textPageHandle: Long, rectIndex: Int): DoubleArray?

    /** Atomic count + get. Flat array, 4 values per rect. */
    @JvmStatic external fun nativeGetRects(textPageHandle: Long, startIndex: Int, count: Int): DoubleArray
}
