package com.example.pdf_reader.pdfium.jni

/** Bindings for `cpp/ppo/import_pages_jni.cpp`. */
internal object ImportNative {
    /** [pageRange]: 1-based, e.g. `"1,3,5-7"`; `null` imports all pages. */
    @JvmStatic external fun nativeImportPages(
        destHandle: Long,
        srcHandle: Long,
        pageRange: String?,
        insertIndex: Int,
    ): Boolean
}
