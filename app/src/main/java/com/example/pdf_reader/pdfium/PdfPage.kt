package com.example.pdf_reader.pdfium

import android.graphics.Bitmap
import com.example.pdf_reader.pdfium.jni.CoreNative
import com.example.pdf_reader.pdfium.jni.PageEditNative
import com.example.pdf_reader.pdfium.jni.RenderNative
import com.example.pdf_reader.pdfium.jni.TextNative

/** Loaded page (FPDF_PAGE). Closing it closes all text pages opened from it. */
class PdfPage internal constructor(
    private val document: PdfDocument,
    index: Int,
    private var handle: Long,
) : AutoCloseable {

    /** Current index in the document; updated on delete/import. */
    var index: Int = index
        internal set

    private val openTextPages = mutableListOf<PdfTextPage>()

    val isClosed: Boolean get() = handle == 0L

    /** 0..3 = 0°, 90°, 180°, 270° clockwise (FPDFPage_SetRotation). */
    var rotation: Int
        get() = CoreNative.nativeGetPageRotation(requireHandle())
        set(value) = PageEditNative.nativeSetRotation(requireHandle(), value)

    val size: PdfSize
        get() = CoreNative.nativeGetPageSize(requireHandle()).let { PdfSize(it[0], it[1]) }

    /**
     * Maps page-space [rects] to normalized rects (0..1, origin top-left) that
     * line up with [renderInto] output. Handles /Rotate and box offsets.
     */
    fun toNorm(rects: List<PdfRect>): List<NormRect> =
        if (rects.isEmpty()) emptyList()
        else CoreNative.nativePageToNormRects(requireHandle(), rects.toFlatArray()).toNormRects()

    /** Maps a normalized point (0..1, origin top-left) to page space `(x, y)` in points. */
    fun normToPage(nx: Double, ny: Double): Pair<Double, Double> =
        CoreNative.nativeNormToPage(requireHandle(), nx, ny).let { it[0] to it[1] }

    /** Loads the text layer (FPDFText_LoadPage). */
    fun openTextPage(): PdfTextPage {
        val textHandle = TextNative.nativeLoadTextPage(requireHandle())
        return PdfTextPage(this, textHandle).also { openTextPages += it }
    }

    /**
     * Renders the whole page scaled to fit [bitmap] (must be ARGB_8888).
     * Progressive: stops early once [token] is cancelled.
     */
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

    /**
     * Renders a fragment of the page as if the whole page were
     * [pageWidthPx] x [pageHeightPx]; the fragment's top-left corner is at
     * ([left], [top]) in those page pixels and its size is the [bitmap] size.
     */
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
