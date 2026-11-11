package com.example.pdf_reader.pdfium.jni

/** Bindings for `cpp/save/save_jni.cpp`. */
internal object SaveNative {
    /** [fd] is written to but not closed. */
    @JvmStatic external fun nativeSaveAsCopy(documentHandle: Long, fd: Int, flags: Int): Boolean
}
