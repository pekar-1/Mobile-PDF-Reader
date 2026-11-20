package com.example.pdf_reader.pdfium.jni

internal object LinkNative {
    // Returns "P:<page>", "U:<url>" or null.
    @JvmStatic external fun nativeLinkAt(
        documentHandle: Long,
        pageHandle: Long,
        textPageHandle: Long,
        x: Double,
        y: Double,
    ): String?
}
