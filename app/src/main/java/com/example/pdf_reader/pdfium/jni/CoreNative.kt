package com.example.pdf_reader.pdfium.jni

/** Bindings for `cpp/core/core_jni.cpp`. */
internal object CoreNative {
    @JvmStatic external fun nativeInitLibrary()
    @JvmStatic external fun nativeDestroyLibrary()

    /** Duplicates [fd] internally; the caller may close its descriptor afterwards. */
    @JvmStatic external fun nativeOpenDocument(fd: Int, password: String?): Long
    @JvmStatic external fun nativeCreateNewDocument(): Long
    @JvmStatic external fun nativeCloseDocument(documentHandle: Long)
    @JvmStatic external fun nativeGetPageCount(documentHandle: Long): Int

    @JvmStatic external fun nativeLoadPage(documentHandle: Long, index: Int): Long
    @JvmStatic external fun nativeClosePage(pageHandle: Long)
    @JvmStatic external fun nativeGetPageRotation(pageHandle: Long): Int

    /** Returns `[width, height]` in PDF points. */
    @JvmStatic external fun nativeGetPageSize(pageHandle: Long): DoubleArray

    /** Returns `[width, height]` in PDF points without loading the page. */
    @JvmStatic external fun nativeGetPageSizeByIndex(documentHandle: Long, index: Int): DoubleArray

    /**
     * Maps flat page-space rects `[l, t, r, b, ...]` (points, origin bottom-left)
     * to normalized rects (0..1, origin top-left), as rendered with rotate = 0.
     */
    @JvmStatic external fun nativePageToNormRects(pageHandle: Long, pageRects: DoubleArray): DoubleArray

    /** Maps a normalized point (0..1, origin top-left) to page space. Returns `[x, y]`. */
    @JvmStatic external fun nativeNormToPage(pageHandle: Long, nx: Double, ny: Double): DoubleArray

    @JvmStatic external fun nativeGetLastError(): Int
}
