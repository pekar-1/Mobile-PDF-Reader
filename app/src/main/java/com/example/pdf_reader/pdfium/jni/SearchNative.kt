package com.example.pdf_reader.pdfium.jni

/** Bindings for `cpp/text/text_search_jni.cpp`. */
internal object SearchNative {
    /** [startIndex]: char index to start from, -1 for end of page. */
    @JvmStatic external fun nativeFindStart(
        textPageHandle: Long,
        query: String,
        flags: Int,
        startIndex: Int,
    ): Long

    @JvmStatic external fun nativeFindNext(searchHandle: Long): Boolean
    @JvmStatic external fun nativeFindPrev(searchHandle: Long): Boolean
    @JvmStatic external fun nativeGetSchResultIndex(searchHandle: Long): Int
    @JvmStatic external fun nativeGetSchCount(searchHandle: Long): Int
    @JvmStatic external fun nativeFindClose(searchHandle: Long)
}
