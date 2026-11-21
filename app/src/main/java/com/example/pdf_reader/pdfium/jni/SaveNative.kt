package com.example.pdf_reader.pdfium.jni

internal object SaveNative {
    @JvmStatic external fun nativeSaveAsCopy(documentHandle: Long, fd: Int, flags: Int): Boolean
}
