package com.example.pdf_reader.pdfium.jni

/** Bindings for `cpp/link/link_jni.cpp`. */
internal object LinkNative {
    /**
     * Link at page point ([x], [y]) in points: `"P:<pageIndex>"`, `"U:<uri>"` or `null`.
     * [textPageHandle] may be 0 to skip detection of plain-text URLs.
     */
    @JvmStatic external fun nativeLinkAt(
        documentHandle: Long,
        pageHandle: Long,
        textPageHandle: Long,
        x: Double,
        y: Double,
    ): String?
}
