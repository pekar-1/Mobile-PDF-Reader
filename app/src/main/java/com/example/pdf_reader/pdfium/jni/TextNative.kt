package com.example.pdf_reader.pdfium.jni

// Rects are [left, top, right, bottom] in PDF points.
internal object TextNative {
    @JvmStatic external fun nativeLoadTextPage(pageHandle: Long): Long
    @JvmStatic external fun nativeCloseTextPage(textPageHandle: Long)
    @JvmStatic external fun nativeCountChars(textPageHandle: Long): Int
    @JvmStatic external fun nativeGetText(textPageHandle: Long, startIndex: Int, count: Int): String
    @JvmStatic external fun nativeGetCharBox(textPageHandle: Long, index: Int): DoubleArray?

    @JvmStatic external fun nativeGetLooseCharBox(textPageHandle: Long, index: Int): DoubleArray?

    // -1 = nothing near, -3 = error
    @JvmStatic external fun nativeGetCharIndexAtPos(
        textPageHandle: Long,
        x: Double,
        y: Double,
        xTolerance: Double,
        yTolerance: Double,
    ): Int

    // 4 values per rect
    @JvmStatic external fun nativeGetRects(textPageHandle: Long, startIndex: Int, count: Int): DoubleArray
}
