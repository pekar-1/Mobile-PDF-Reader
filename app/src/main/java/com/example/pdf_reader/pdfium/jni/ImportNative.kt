package com.example.pdf_reader.pdfium.jni

internal object ImportNative {
    @JvmStatic external fun nativeImportPages(
        destHandle: Long,
        srcHandle: Long,
        pageRange: String?,
        insertIndex: Int,
    ): Boolean
}
