package com.example.pdf_reader.pdfium

import android.graphics.Bitmap
import android.util.LruCache

internal data class RenderKey(val version: Int, val pageIndex: Int, val widthPx: Int)

// LRU of rendered pages. Pinned pages (around the screen) are never evicted.
// No bitmap.recycle(), Compose may still be drawing an evicted one.
internal class PdfBitmapCache(maxBytes: Int = defaultMaxBytes()) {

    private val lru = object : LruCache<RenderKey, Bitmap>(maxBytes) {
        override fun sizeOf(key: RenderKey, value: Bitmap): Int = value.allocationByteCount
    }

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

    // Pages not rendered yet get pinned later in put().
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
