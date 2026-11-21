package com.example.pdf_reader.pdfium.jni

import android.graphics.Bitmap

internal object RenderNative {
    // Same values as in render_jni.cpp
    const val RESULT_DONE = 0
    const val RESULT_CANCELLED = 1
    const val RESULT_FAILED = 2

    const val FLAG_ANNOT = 0x01
    const val FLAG_LCD_TEXT = 0x02

    @JvmStatic external fun nativeCreateCancelToken(): Long

    @JvmStatic external fun nativeCancel(tokenHandle: Long)
    @JvmStatic external fun nativeDestroyCancelToken(tokenHandle: Long)

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
