package com.example.pdf_reader.ui.pdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

internal data class PageHit(val pageIndex: Int, val nx: Float, val ny: Float)

// Positions of visible pages, to map a touch point to a page.
internal class PageRegistry {
    private val pages = HashMap<Int, LayoutCoordinates>()

    // +1 when any page moves
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

    // Between pages it picks the nearest one, so dragging in the gap still works.
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
