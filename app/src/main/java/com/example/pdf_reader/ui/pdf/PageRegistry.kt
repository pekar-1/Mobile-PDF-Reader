package com.example.pdf_reader.ui.pdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

/** A point on a page in normalized coordinates (0..1, origin top-left). */
internal data class PageHit(val pageIndex: Int, val nx: Float, val ny: Float)

/**
 * Coordinates of the pages currently composed, so gestures and handles can
 * map between window coordinates and pages. Works with any zoom or pinch
 * transform, since all mapping goes through [LayoutCoordinates].
 */
internal class PageRegistry {
    private val pages = HashMap<Int, LayoutCoordinates>()

    /** Incremented whenever a page moves; read it to follow scrolling and zoom. */
    var tick by mutableIntStateOf(0)
        private set

    fun update(pageIndex: Int, coordinates: LayoutCoordinates) {
        pages[pageIndex] = coordinates
        tick++
    }

    fun remove(pageIndex: Int, coordinates: LayoutCoordinates?) {
        if (coordinates == null || pages[pageIndex] === coordinates) pages.remove(pageIndex)
    }

    fun coordinates(pageIndex: Int): LayoutCoordinates? = pages[pageIndex]?.takeIf { it.isAttached }

    /**
     * Page under [window] position. Between pages (or beside them) it picks
     * the vertically nearest page and clamps the point onto it, so drags in
     * gaps still extend the selection.
     */
    fun hitTest(window: Offset): PageHit? {
        var best: PageHit? = null
        var bestDistance = Float.MAX_VALUE
        for ((index, coords) in pages) {
            if (!coords.isAttached) continue
            val local = coords.windowToLocal(window)
            val w = coords.size.width.toFloat()
            val h = coords.size.height.toFloat()
            if (w <= 0f || h <= 0f) continue
            val distance = when {
                local.y < 0f -> -local.y
                local.y > h -> local.y - h
                else -> 0f
            }
            if (distance < bestDistance) {
                bestDistance = distance
                best = PageHit(index, (local.x / w).coerceIn(0f, 1f), (local.y / h).coerceIn(0f, 1f))
            }
        }
        return best
    }
}
