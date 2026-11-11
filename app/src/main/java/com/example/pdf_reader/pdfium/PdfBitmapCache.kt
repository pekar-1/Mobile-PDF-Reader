package com.example.pdf_reader.pdfium

import android.graphics.Bitmap
import android.util.LruCache

/** Identifies one rendered bitmap. [version] changes after every document edit. */
internal data class RenderKey(val version: Int, val pageIndex: Int, val widthPx: Int)

/**
 * Thread-safe LRU of rendered pages, bounded by bitmap bytes. Bitmaps are
 * never recycled manually: Compose may still draw an evicted one, so the GC
 * frees them once unreferenced.
 *
 * Pinned keys (the page window around the viewport) are additionally held by
 * strong references, so LRU pressure cannot drop them. Pinned bitmaps do not
 * count towards the LRU limit; the window is only a few pages.
 */
internal class PdfBitmapCache(maxBytes: Int = defaultMaxBytes()) {

    private val lru = object : LruCache<RenderKey, Bitmap>(maxBytes) {
        override fun sizeOf(key: RenderKey, value: Bitmap): Int = value.allocationByteCount
    }

    // Guarded by lock.
    private var pinnedKeys: Set<RenderKey> = emptySet()
    private val pinned = HashMap<RenderKey, Bitmap>()
    private val lock = Any()

    operator fun get(key: RenderKey): Bitmap? =
        lru.get(key) ?: synchronized(lock) { pinned[key] }

    fun put(key: RenderKey, bitmap: Bitmap) {
        lru.put(key, bitmap)
        synchronized(lock) {
            if (key in pinnedKeys) pinned[key] = bitmap // Finished after pin().
        }
    }

    /** Replaces the pinned set. Keys not rendered yet get pinned when [put]. */
    fun pin(keys: Set<RenderKey>) = synchronized(lock) {
        pinned.keys.retainAll(keys)
        for (key in keys) {
            if (key !in pinned) lru.get(key)?.let { pinned[key] = it }
        }
        pinnedKeys = keys
    }

    fun evictAll() {
        synchronized(lock) {
            pinned.clear()
            pinnedKeys = emptySet()
        }
        lru.evictAll()
    }

    companion object {
        fun defaultMaxBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
