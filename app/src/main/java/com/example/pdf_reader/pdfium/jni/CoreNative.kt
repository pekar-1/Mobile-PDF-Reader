package com.example.pdf_reader.pdfium.jni

internal object CoreNative {
    @JvmStatic external fun nativeInitLibrary()
    @JvmStatic external fun nativeDestroyLibrary()

    @JvmStatic external fun nativeOpenDocument(fd: Int, password: String?): Long
    @JvmStatic external fun nativeCreateNewDocument(): Long
    @JvmStatic external fun nativeCloseDocument(documentHandle: Long)
    @JvmStatic external fun nativeGetPageCount(documentHandle: Long): Int

    @JvmStatic external fun nativeLoadPage(documentHandle: Long, index: Int): Long
    @JvmStatic external fun nativeClosePage(pageHandle: Long)
    @JvmStatic external fun nativeGetPageRotation(pageHandle: Long): Int

    @JvmStatic external fun nativeGetPageSize(pageHandle: Long): DoubleArray

    @JvmStatic external fun nativeGetPageSizeByIndex(documentHandle: Long, index: Int): DoubleArray

    @JvmStatic external fun nativePageToNormRects(pageHandle: Long, pageRects: DoubleArray): DoubleArray

    @JvmStatic external fun nativeNormToPage(pageHandle: Long, nx: Double, ny: Double): DoubleArray

    @JvmStatic external fun nativeGetLastError(): Int
}
