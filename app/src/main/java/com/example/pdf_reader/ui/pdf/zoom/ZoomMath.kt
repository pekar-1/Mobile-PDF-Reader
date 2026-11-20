package com.example.pdf_reader.ui.pdf.zoom

import kotlin.math.roundToInt

// Zoom math without Compose, so it can be tested on JVM.
// Column is viewportWidth * scale wide, shifted left by offsetX.
object ZoomMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 5f
    const val DOUBLE_TAP_SCALE = 2.5f

    // Render resolutions, as multiples of the fit-width page width
    val RENDER_BUCKETS = floatArrayOf(1f, 1.5f, 2f, 3f, 4f, 5f)

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    // Smallest bucket >= scale, so the bitmap is never upscaled.
    fun renderBucket(scale: Float): Float =
        RENDER_BUCKETS.firstOrNull { it >= scale - 1e-3f } ?: RENDER_BUCKETS.last()

    fun renderWidthPx(basePageWidthPx: Int, scale: Float): Int =
        (basePageWidthPx * renderBucket(scale)).roundToInt().coerceAtLeast(1)

    fun columnWidth(viewportWidth: Float, scale: Float): Float = viewportWidth * scale

    fun pageWidth(viewportWidth: Float, scale: Float, padding: Float): Float =
        (columnWidth(viewportWidth, scale) - 2 * padding).coerceAtLeast(1f)

    fun maxOffsetX(viewportWidth: Float, scale: Float): Float =
        (columnWidth(viewportWidth, scale) - viewportWidth).coerceAtLeast(0f)

    fun clampOffsetX(offsetX: Float, viewportWidth: Float, scale: Float): Float =
        offsetX.coerceIn(0f, maxOffsetX(viewportWidth, scale))

    // New offsetX after zoom, so the point under the fingers stays under them.
    fun anchoredOffsetX(
        offsetX: Float,
        from: Float,
        to: Float,
        focusBefore: Float,
        focusAfter: Float,
        viewportWidth: Float,
        padding: Float,
    ): Float {
        val ratio = pageWidth(viewportWidth, to, padding) / pageWidth(viewportWidth, from, padding)
        val contentX = offsetX + focusBefore // In the old column.
        val newContentX = padding + (contentX - padding) * ratio
        return clampOffsetX(newContentX - focusAfter, viewportWidth, to)
    }

    // Same for vertical: returns (index, scrollOffset) for requestScrollToItem.
    // Goes back to earlier items when needed, because scroll offset can't be < 0.
    fun anchoredScroll(
        itemIndex: Int,
        offsetInItem: Float,
        focusY: Float,
        topPadding: Float,
        spacing: Float,
        itemHeight: (Int) -> Float,
    ): Pair<Int, Int> {
        // Item top must land at focusY - offsetInItem; scroll offset 0 puts it at topPadding.
        var index = itemIndex
        var offset = offsetInItem - focusY + topPadding
        while (offset < 0 && index > 0) {
            index--
            offset += itemHeight(index) + spacing
        }
        return index to offset.coerceAtLeast(0f).roundToInt()
    }
}
