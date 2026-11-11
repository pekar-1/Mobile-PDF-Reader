package com.example.pdf_reader.pdfium

import com.example.pdf_reader.pdfium.jni.RenderNative
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native cancellation flag polled by PDFium during progressive rendering.
 * [cancel] is thread-safe and lock-free. [close] must run after the render
 * that uses the token has returned.
 */
internal class PdfRenderCancelToken : AutoCloseable {

    private val closed = AtomicBoolean(false)

    val handle: Long = RenderNative.nativeCreateCancelToken()

    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
        // Synchronized with close() so the native token is never freed
        // while cancel() is writing to it.
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

/** Outcome of [PdfPage.renderInto]. */
internal enum class PdfRenderResult { DONE, CANCELLED, FAILED }
