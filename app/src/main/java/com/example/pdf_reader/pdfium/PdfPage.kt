package com.example.pdf_reader.pdfium

import android.graphics.Bitmap
import com.example.pdf_reader.pdfium.jni.CoreNative
import com.example.pdf_reader.pdfium.jni.LinkNative
import com.example.pdf_reader.pdfium.jni.PageEditNative
import com.example.pdf_reader.pdfium.jni.RenderNative
import com.example.pdf_reader.pdfium.jni.TextNative

class PdfPage internal constructor(
    private val document: PdfDocument,
    index: Int,
    private var handle: Long,
) : AutoCloseable {

    var index: Int = index
        internal set

    private val openTextPages = mutableListOf<PdfTextPage>()

    val isClosed: Boolean get() = handle == 0L

    // 0..3 = 0, 90, 180, 270 degrees
    var rotation: Int
        get() = CoreNative.nativeGetPageRotation(requireHandle())
        set(value) = PageEditNative.nativeSetRotation(requireHandle(), value)

    val size: PdfSize
        get() = CoreNative.nativeGetPageSize(requireHandle()).let { PdfSize(it[0], it[1]) }

    // PDF points -> 0..1 (top-left origin), same as the rendered bitmap.
    fun toNorm(rects: List<PdfRect>): List<NormRect> =
        if (rects.isEmpty()) emptyList()
        else CoreNative.nativePageToNormRects(requireHandle(), rects.toFlatArray()).toNormRects()

    fun normToPage(nx: Double, ny: Double): Pair<Double, Double> =
        CoreNative.nativeNormToPage(requireHandle(), nx, ny).let { it[0] to it[1] }

    // textPage is needed to find plain-text URLs.
    fun linkAt(nx: Double, ny: Double, textPage: PdfTextPage?): PdfLink? {
        val (x, y) = normToPage(nx, ny)
        val raw = LinkNative.nativeLinkAt(
            document.requireHandle(), requireHandle(), textPage?.requireHandle() ?: 0L, x, y,
        )
        return PdfLink.parse(raw)
    }

    fun openTextPage(): PdfTextPage {
        val textHandle = TextNative.nativeLoadTextPage(requireHandle())
        return PdfTextPage(this, textHandle).also { openTextPages += it }
    }

    // Bitmap must be ARGB_8888.
    internal fun renderInto(
        bitmap: Bitmap,
        token: PdfRenderCancelToken,
        flags: Int = RenderNative.FLAG_ANNOT,
    ): PdfRenderResult {
        val result = RenderNative.nativeRenderPage(
            requireHandle(), bitmap, 0, 0, bitmap.width, bitmap.height, 0, flags, token.handle,
        )
        return when (result) {
            RenderNative.RESULT_DONE -> PdfRenderResult.DONE
            RenderNative.RESULT_CANCELLED -> PdfRenderResult.CANCELLED
            else -> PdfRenderResult.FAILED
        }
    }

    // Renders a part of a page that is pageWidthPx x pageHeightPx big, starting at (left, top).
    internal fun renderRegionInto(
        bitmap: Bitmap,
        pageWidthPx: Int,
        pageHeightPx: Int,
        left: Int,
        top: Int,
        token: PdfRenderCancelToken,
        flags: Int = RenderNative.FLAG_ANNOT,
    ): PdfRenderResult {
        val result = RenderNative.nativeRenderPage(
            requireHandle(), bitmap, -left, -top, pageWidthPx, pageHeightPx, 0, flags, token.handle,
        )
        return when (result) {
            RenderNative.RESULT_DONE -> PdfRenderResult.DONE
            RenderNative.RESULT_CANCELLED -> PdfRenderResult.CANCELLED
            else -> PdfRenderResult.FAILED
        }
    }

    override fun close() {
        if (handle == 0L) return
        openTextPages.toList().forEach { it.close() }
        CoreNative.nativeClosePage(handle)
        handle = 0L
        document.onPageClosed(this)
    }

    internal fun requireHandle(): Long {
        check(handle != 0L) { "Page is closed" }
        return handle
    }

    internal fun onTextPageClosed(textPage: PdfTextPage) {
        openTextPages.remove(textPage)
    }
}
