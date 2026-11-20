package com.example.pdf_reader.pdfium.jni

internal object PageEditNative {
    @JvmStatic external fun nativeDeletePage(documentHandle: Long, index: Int)

    @JvmStatic external fun nativeSetRotation(pageHandle: Long, rotation: Int)
}
