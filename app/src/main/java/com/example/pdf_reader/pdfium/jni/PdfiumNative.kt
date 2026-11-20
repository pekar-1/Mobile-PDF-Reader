package com.example.pdf_reader.pdfium.jni

// Call ensureInitialized() before any other *Native.
internal object PdfiumNative {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            System.loadLibrary("pdfium")
            System.loadLibrary("pdf_engine") // JNI_OnLoad registers all modules.
            CoreNative.nativeInitLibrary()
            initialized = true
        }
    }
}
