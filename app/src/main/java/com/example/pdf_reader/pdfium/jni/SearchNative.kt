package com.example.pdf_reader.pdfium.jni

internal object SearchNative {
    @JvmStatic external fun nativeFindStart(
        textPageHandle: Long,
        query: String,
        flags: Int,
        startIndex: Int,
    ): Long

    @JvmStatic external fun nativeFindNext(searchHandle: Long): Boolean
    @JvmStatic external fun nativeGetSchResultIndex(searchHandle: Long): Int
    @JvmStatic external fun nativeGetSchCount(searchHandle: Long): Int
    @JvmStatic external fun nativeFindClose(searchHandle: Long)
}
