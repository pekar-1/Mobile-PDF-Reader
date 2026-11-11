package com.example.pdf_reader.ui.pdf.zoom

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {

    private val viewport = 1000f
    private val padding = 20f

    @Test
    fun clampsScale() {
        assertEquals(1f, ZoomMath.clampScale(0.3f))
        assertEquals(5f, ZoomMath.clampScale(9f))
        assertEquals(2.2f, ZoomMath.clampScale(2.2f))
    }

    @Test
    fun renderBucketNeverUpscales() {
        assertEquals(1f, ZoomMath.renderBucket(1f))
        assertEquals(1.5f, ZoomMath.renderBucket(1.2f))
        assertEquals(3f, ZoomMath.renderBucket(2.01f))
        assertEquals(5f, ZoomMath.renderBucket(5f))
        assertEquals(1440, ZoomMath.renderWidthPx(960, 1.4f))
    }

    @Test
    fun clampsOffsetToColumn() {
        assertEquals(0f, ZoomMath.clampOffsetX(-5f, viewport, 2f))
        assertEquals(1000f, ZoomMath.clampOffsetX(5000f, viewport, 2f))
        assertEquals(0f, ZoomMath.clampOffsetX(300f, viewport, 1f))
    }

    @Test
    fun anchorKeepsContentUnderFocus() {
        // At 1x, viewport x 500 is page x 480. At 2x that page point is at
        // column x 20 + 480 * (1960 / 960); it must stay at viewport x 500.
        val offset = ZoomMath.anchoredOffsetX(0f, 1f, 2f, 500f, 500f, viewport, padding)
        val pageXAt2 = 480f * (1960f / 960f)
        assertEquals(padding + pageXAt2 - 500f, offset, 0.01f)
    }

    @Test
    fun anchorFollowsMovedFocusAndClamps() {
        val offset = ZoomMath.anchoredOffsetX(0f, 1f, 2f, 10f, 900f, viewport, padding)
        assertEquals(0f, offset) // Would be negative: clamped to the left edge.
    }

    @Test
    fun anchoredScrollWithinItem() {
        // Point 600 px into item 3 must land at viewport y 200 (top padding 20).
        assertEquals(3 to 420, ZoomMath.anchoredScroll(3, 600f, 200f, 20f, 20f) { 1000f })
    }

    @Test
    fun anchoredScrollWalksBackOverEarlierItems() {
        // 100 px into item 3 at viewport y 500: item 3's top must be at y 400,
        // so item 2 (1000 px + 20 gap) is scrolled 1020 - 380 = 640 px.
        assertEquals(2 to 640, ZoomMath.anchoredScroll(3, 100f, 500f, 20f, 20f) { 1000f })
    }

    @Test
    fun anchoredScrollStopsAtFirstItem() {
        assertEquals(0 to 0, ZoomMath.anchoredScroll(0, 10f, 500f, 20f, 20f) { 1000f })
    }
}
