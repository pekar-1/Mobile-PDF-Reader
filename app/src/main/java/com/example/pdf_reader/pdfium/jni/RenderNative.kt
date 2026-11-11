package com.example.pdf_reader.pdfium.jni

import android.graphics.Bitmap

/** Bindings for `cpp/render/render_jni.cpp`. */
internal object RenderNative {
    /** Result codes of [nativeRenderPage]; keep in sync with render_jni.cpp. */
    const val RESULT_DONE = 0
    const val RESULT_CANCELLED = 1
    const val RESULT_FAILED = 2

    /** Same values as `FPDF_ANNOT` / `FPDF_LCD_TEXT` in fpdfview.h. */
    const val FLAG_ANNOT = 0x01
    const val FLAG_LCD_TEXT = 0x02

    @JvmStatic external fun nativeCreateCancelToken(): Long

    /** Lock-free; safe to call from any thread while a render is running. */
    @JvmStatic external fun nativeCancel(tokenHandle: Long)
    @JvmStatic external fun nativeDestroyCancelToken(tokenHandle: Long)

    /**
     * Renders into an ARGB_8888 [bitmap]. Page area in bitmap pixels:
     * ([startX], [startY], [sizeX], [sizeY]). [rotate]: 0..3, extra rotation.
     */
    @JvmStatic external fun nativeRenderPage(
        pageHandle: Long,
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int,
        tokenHandle: Long,
    ): Int
}
