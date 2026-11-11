package com.example.pdf_reader.pdfium.jni

/** Bindings for `cpp/edit/page_edit_jni.cpp`. */
internal object PageEditNative {
    /** The page at [index] must not be loaded. */
    @JvmStatic external fun nativeDeletePage(documentHandle: Long, index: Int)

    /** [rotation]: 0..3, multiples of 90° clockwise. */
    @JvmStatic external fun nativeSetRotation(pageHandle: Long, rotation: Int)
}
