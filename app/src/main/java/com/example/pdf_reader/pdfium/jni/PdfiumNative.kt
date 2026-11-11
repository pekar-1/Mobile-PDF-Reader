package com.example.pdf_reader.pdfium.jni

/**
 * Loads the native libraries and initializes PDFium once per process.
 * Call [ensureInitialized] before using any other `*Native` object.
 */
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
