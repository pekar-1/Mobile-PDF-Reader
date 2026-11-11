package com.example.pdf_reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdf_reader.pdfium.PdfDocument
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.pdfium.PdfRenderCancelToken
import com.example.pdf_reader.pdfium.PdfRenderResult
import com.example.pdf_reader.pdfium.PdfSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfDocumentManagerTest {

    private lateinit var sample: File
    private lateinit var manager: PdfDocumentManager

    @Before
    fun setUp() = runBlocking {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        sample = File(cacheDir, "manager-sample.pdf").apply { writeBytes(TestPdfs.build()) }
        manager = PdfDocumentManager.open(sample)
    }

    @After
    fun tearDown() = manager.close()

    @Test
    fun rendersPageToBitmapWithText() = runBlocking {
        assertEquals(3, manager.pageCount.value)
        val bitmap = manager.renderBitmap(0, 600)
        assertEquals(600, bitmap.width)
        assertEquals(776, bitmap.height) // 600 * 792 / 612
        assertTrue("page should contain dark text pixels", bitmap.hasDarkPixel())

        // Same request again comes from the cache.
        assertTrue(manager.renderBitmap(0, 600) === bitmap)
        assertEquals(1, manager.stats.cacheHits.get())
    }

    @Test
    fun cancelledTokenStopsNativeRenderBeforeDrawing() {
        PdfDocument.open(sample).use { doc ->
            doc.openPage(0).use { page ->
                PdfRenderCancelToken().use { token ->
                    token.cancel()
                    val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
                    assertEquals(PdfRenderResult.CANCELLED, page.renderInto(bitmap, token))
                    assertEquals(0, bitmap.getPixel(100, 100)) // Not even filled white.
                }
            }
        }
    }

    @Test
    fun abandonedRendersAreSkipped() = runBlocking {
        // UNDISPATCHED: each request is registered before the loop moves on.
        val jobs = (1..30).map { i ->
            launch(start = CoroutineStart.UNDISPATCHED) { manager.renderBitmap(i % 3, 100 + i) }
        }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }

        // Runs after every abandoned task in the FIFO queue.
        val bitmap = manager.renderBitmap(1, 300)
        assertEquals(300, bitmap.width)

        val stats = manager.stats
        assertEquals(31, stats.started.get() + stats.cancelledQueued.get())
        assertEquals(stats.started.get(), stats.completed.get() + stats.cancelledNative.get())
        assertTrue("some renders should be dropped while queued", stats.cancelledQueued.get() > 0)
    }

    @Test
    fun identicalRequestsShareOneRender() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { manager.renderBitmap(2, 400) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { manager.renderBitmap(2, 400) }
        val third = async(start = CoroutineStart.UNDISPATCHED) { manager.renderBitmap(2, 400) }
        first.cancel() // Others still wait, so the shared render must go on.

        val result = second.await()
        assertTrue(third.await() === result)
        assertEquals(1, manager.stats.started.get())
        assertEquals(1, manager.stats.completed.get())
    }

    @Test
    fun parallelCallersAreSerialized() = runBlocking {
        val results = (0 until 50).map { i ->
            async(Dispatchers.Default) {
                if (i % 2 == 0) {
                    manager.renderBitmap(i % 3, 200 + (i % 5) * 10).width
                } else {
                    manager.search(i % 3, "search target").size
                }
            }
        }.awaitAll()

        results.forEachIndexed { i, value ->
            if (i % 2 == 0) assertEquals(200 + (i % 5) * 10, value)
            else assertEquals(if (i % 3 == 0) 0 else 1, value)
        }
        assertEquals(TestPdfs.PAGES[2], manager.text(2).trim())
    }

    @Test
    fun editInvalidatesRenders() = runBlocking {
        val before = manager.renderBitmap(0, 600)
        val version = manager.version.value

        manager.setRotation(0, 1)

        assertEquals(version + 1, manager.version.value)
        assertEquals(PdfSize(792.0, 612.0), manager.pageSizes.value[0])
        val after = manager.renderBitmap(0, 600)
        assertFalse(after === before)
        assertEquals(464, after.height) // 600 * 612 / 792

        manager.deletePage(2)
        assertEquals(2, manager.pageCount.value)
    }

    @Test
    fun closeDuringRendersIsSafe() = runBlocking {
        val outcomes = (1..10).map { i ->
            async(Dispatchers.Default) { runCatching { manager.renderBitmap(i % 3, 500 + i) } }
        }
        manager.close()
        outcomes.awaitAll().forEach { outcome ->
            val error = outcome.exceptionOrNull() ?: return@forEach
            assertTrue(
                "unexpected $error",
                error is CancellationException || error is IllegalStateException,
            )
        }
        assertTrue(manager.isClosed)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.renderBitmap(0, 100) }
        }
        manager.close() // Idempotent.
    }

    private fun Bitmap.hasDarkPixel(): Boolean {
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            val p = getPixel(x, y)
            if (Color.red(p) < 128 && Color.green(p) < 128 && Color.blue(p) < 128) return true
        }
        return false
    }
}
