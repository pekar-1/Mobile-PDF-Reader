package com.example.pdf_reader.pdfium

import com.example.pdf_reader.pdfium.jni.RenderNative
import java.util.concurrent.atomic.AtomicBoolean

// PDFium checks this flag while rendering. Close it only after the render returned.
internal class PdfRenderCancelToken : AutoCloseable {

    private val closed = AtomicBoolean(false)

    val handle: Long = RenderNative.nativeCreateCancelToken()

    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
        // So close() can't free the token while we write to it.
        synchronized(this) {
            if (!closed.get()) RenderNative.nativeCancel(handle)
        }
    }

    override fun close() {
        synchronized(this) {
            if (closed.compareAndSet(false, true)) RenderNative.nativeDestroyCancelToken(handle)
        }
    }
}

internal enum class PdfRenderResult { DONE, CANCELLED, FAILED }
