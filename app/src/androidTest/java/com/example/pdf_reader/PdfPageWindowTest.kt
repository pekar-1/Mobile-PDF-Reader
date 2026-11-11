package com.example.pdf_reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdf_reader.pdfium.PdfDocumentManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Prefetch of the next pages and retention of the previous ones. */
@RunWith(AndroidJUnit4::class)
class PdfPageWindowTest {

    private lateinit var sample: File
    private lateinit var manager: PdfDocumentManager

    @Before
    fun setUp() = runBlocking {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        sample = File(cacheDir, "window-sample.pdf").apply {
            writeBytes(TestPdfs.build(List(PAGES) { "Page number ${it + 1}" }))
        }
        manager = PdfDocumentManager.open(sample)
    }

    @After
    fun tearDown() = manager.close()

    @Test
    fun prefetchRendersNextTwoPages() = runBlocking {
        manager.setViewport(2, 3, WIDTH)
        waitUntil { manager.stats.prefetchCompleted.get() == 2 }

        assertNotNull(manager.cachedPage(4, WIDTH))
        assertNotNull(manager.cachedPage(5, WIDTH))
        assertNull(manager.cachedPage(6, WIDTH))
        assertNull(manager.cachedPage(2, WIDTH)) // Visible pages are rendered on demand only.
        assertEquals(2, manager.stats.prefetchStarted.get())
    }

    @Test
    fun previousPagesStayPinned() = runBlocking {
        manager.close()
        manager = PdfDocumentManager.open(sample, cacheMaxBytes = ONE_BITMAP_BYTES)

        for (i in 0..3) {
            manager.setViewport(i, i, WIDTH)
            manager.renderBitmap(i, WIDTH)
        }
        waitUntil { manager.cachedPage(4, WIDTH) != null && manager.cachedPage(5, WIDTH) != null }

        // LRU holds a single bitmap; the window 1..5 survives through pins.
        for (i in 1..5) assertNotNull("page $i", manager.cachedPage(i, WIDTH))
        assertNull("page 0 left the window", manager.cachedPage(0, WIDTH))
    }

    @Test
    fun windowIsClippedToDocument() = runBlocking {
        manager.setViewport(PAGES - 1, PAGES - 1, WIDTH)
        delay(300)
        assertEquals(0, manager.stats.prefetchStarted.get())

        manager.setViewport(0, 0, WIDTH)
        waitUntil { manager.stats.prefetchCompleted.get() == 2 }
        assertNotNull(manager.cachedPage(1, WIDTH))
        assertNotNull(manager.cachedPage(2, WIDTH))
        assertNull(manager.cachedPage(3, WIDTH))
    }

    @Test
    fun viewportJumpsCancelPrefetch() = runBlocking {
        repeat(20) { i -> manager.setViewport(i % 2 * 5, i % 2 * 5, WIDTH + i) }
        manager.setViewport(0, 0, WIDTH)
        waitUntil { manager.cachedPage(1, WIDTH) != null && manager.cachedPage(2, WIDTH) != null }

        val stats = manager.stats
        waitUntil { stats.started.get() == stats.completed.get() + stats.cancelledNative.get() }
        assertTrue("stale prefetches should be dropped", stats.prefetchStarted.get() < 40)
    }

    @Test
    fun visibleRenderGoesBeforePrefetch() = runBlocking {
        // Registers visible demand before the window is known.
        val visible = async(start = CoroutineStart.UNDISPATCHED) { manager.renderBitmap(0, 2000) }
        manager.setViewport(0, 0, 2000)
        while (visible.isActive) {
            assertEquals("prefetch started during a visible render", 0, manager.stats.prefetchStarted.get())
            delay(5)
        }
        assertEquals(2000, visible.await().width)
        waitUntil { manager.stats.prefetchCompleted.get() == 2 }
    }

    @Test
    fun editReappliesWindow() = runBlocking {
        manager.setViewport(0, 0, WIDTH)
        waitUntil { manager.stats.prefetchCompleted.get() == 2 }

        manager.setRotation(1, 1) // New version: old bitmaps are gone, window re-prefetched.
        waitUntil { manager.cachedPage(1, WIDTH) != null }
        assertEquals(ROTATED_HEIGHT, manager.cachedPage(1, WIDTH)!!.height)
    }

    @Test
    fun closeDuringPrefetchIsSafe() = runBlocking {
        manager.setViewport(0, 0, 3000)
        manager.close()
        manager.setViewport(1, 1, WIDTH) // Ignored after close.
        assertNull(manager.cachedPage(1, WIDTH))
        assertTrue(manager.isClosed)
    }

    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(20)
    }

    private companion object {
        const val PAGES = 8
        const val WIDTH = 300
        const val ROTATED_HEIGHT = 232 // 300 * 612 / 792
        const val ONE_BITMAP_BYTES = 500_000 // 300x388 ARGB = 465 600 B
    }
}
