package com.example.pdf_reader

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdf_reader.pdfium.PdfDocument
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.pdfium.TextPosition
import com.example.pdf_reader.pdfium.TextRange
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Page <-> normalized geometry, hit-testing, words and document-wide search.
 * Test text starts at (72, 700) pt on 612x792 pt pages (see [TestPdfs]).
 */
@RunWith(AndroidJUnit4::class)
class TextGeometryTest {

    private val cacheDir: File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    private fun pdf(name: String, rotate: Int = 0) =
        File(cacheDir, name).apply { writeBytes(TestPdfs.build(rotate = rotate)) }

    @Test
    fun charBoxCenterHitsSameChar() {
        PdfDocument.open(pdf("geom-hit.pdf")).use { doc ->
            doc.openPage(0).use { page ->
                val text = page.openTextPage()
                for (i in 0 until 5) {
                    val box = text.charBox(i)!!
                    val x = (box.left + box.right) / 2
                    val y = (box.top + box.bottom) / 2
                    assertEquals(i, text.charIndexAt(x, y, 1.0, 1.0))
                }
                assertNull(text.charIndexAt(500.0, 100.0, 1.0, 1.0))
            }
        }
    }

    @Test
    fun normRectMatchesPagePositionWithoutRotation() {
        PdfDocument.open(pdf("geom-0.pdf")).use { doc ->
            doc.openPage(0).use { page ->
                val box = page.openTextPage().charBox(0)!!
                val norm = page.toNorm(listOf(box)).single()
                assertEquals(72.0 / 612, norm.left.toDouble(), 0.01)
                assertEquals(1 - 700.0 / 792, norm.bottom.toDouble(), 0.01) // Baseline.
                assertTrue(norm.top < norm.bottom && norm.left < norm.right)
            }
        }
    }

    @Test
    fun normRectFollowsPageRotation() {
        PdfDocument.open(pdf("geom-90.pdf", rotate = 90)).use { doc ->
            doc.openPage(0).use { page ->
                val box = page.openTextPage().charBox(0)!!
                val norm = page.toNorm(listOf(box)).single()
                // /Rotate 90 turns the page clockwise: (u, v) -> (1 - v, u).
                assertEquals(1 - (1 - 700.0 / 792), norm.left.toDouble(), 0.01)
                assertEquals(72.0 / 612, norm.top.toDouble(), 0.01)
                assertTrue(norm.top < norm.bottom && norm.left < norm.right)
            }
        }
    }

    @Test
    fun normToPageInvertsToNorm() {
        for (rotate in listOf(0, 90, 180, 270)) {
            PdfDocument.open(pdf("geom-rt-$rotate.pdf", rotate)).use { doc ->
                doc.openPage(0).use { page ->
                    val box = page.openTextPage().charBox(3)!!
                    val norm = page.toNorm(listOf(box)).single()
                    val (x, y) = page.normToPage(norm.centerX.toDouble(), norm.centerY.toDouble())
                    assertEquals("x, rotate $rotate", (box.left + box.right) / 2, x, 0.01)
                    assertEquals("y, rotate $rotate", (box.top + box.bottom) / 2, y, 0.01)
                }
            }
        }
    }

    @Test
    fun managerHitTestsWordsAndRects() = runBlocking {
        PdfDocumentManager.open(pdf("geom-mgr.pdf")).use { manager ->
            // "Hello PDFium page one": "PDFium" is chars 6..11.
            val pdfiumRects = manager.rects(TextRange(0, 6, 12))
            assertTrue(pdfiumRects.isNotEmpty())
            val r = pdfiumRects.first()
            assertTrue(r.left in 0f..1f && r.right in 0f..1f && r.top in 0f..1f && r.bottom in 0f..1f)

            assertEquals(TextRange(0, 6, 12), manager.wordAt(0, r.centerX, r.centerY))
            assertEquals(0, manager.charAt(0, r.centerX, r.centerY)?.pageIndex)
            assertNull(manager.charAt(0, 0.9f, 0.9f))
            assertNotNull(manager.charBox(TextPosition(0, 0)))
        }
    }

    @Test
    fun managerJoinsTextAcrossPages() = runBlocking {
        PdfDocumentManager.open(pdf("geom-text.pdf")).use { manager ->
            val text = manager.text(listOf(TextRange(0, 18, 21), TextRange(1, 0, 6)))
            assertEquals("one\nSecond", text)
        }
    }

    @Test
    fun searchDocumentWrapsFromStartPage() = runBlocking {
        PdfDocumentManager.open(pdf("geom-search.pdf")).use { manager ->
            val results = manager.searchDocument("search target", startPage = 2).toList()
            assertEquals(listOf(2, 0, 1), results.map { it.pageIndex })
            assertEquals(listOf(1, 2, 3), results.map { it.pagesScanned })
            assertEquals(listOf(1, 0, 1), results.map { it.hits.size })
            val hit = results.first().hits.single()
            assertEquals(2, hit.pageIndex)
            assertEquals("search target".length, hit.charCount)
            assertTrue(hit.rects.isNotEmpty())
        }
    }

    @Test
    fun regionRenderMatchesCropOfFullRender() = runBlocking {
        PdfDocumentManager.open(pdf("geom-region.pdf")).use { manager ->
            val full = manager.renderBitmap(0, 1200)
            val region = android.graphics.Rect(100, 50, 500, 250) // Covers the text line.
            val patch = manager.renderRegion(0, 1200, region).asAndroidBitmap()
            assertEquals(region.width(), patch.width)
            assertEquals(region.height(), patch.height)
            var dark = 0
            var mismatched = 0
            for (y in 0 until patch.height step 2) for (x in 0 until patch.width step 2) {
                val a = patch.getPixel(x, y)
                val b = full.getPixel(region.left + x, region.top + y)
                if (android.graphics.Color.red(a) < 128) dark++
                if (kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) > 40) mismatched++
            }
            assertTrue("patch should contain text", dark > 0)
            assertTrue("patch differs from crop in $mismatched samples", mismatched < dark / 10 + 5)
        }
    }

    @Test
    fun largeWidthIsCapped() = runBlocking {
        PdfDocumentManager.open(pdf("geom-cap.pdf")).use { manager ->
            assertTrue(manager.isRenderCapped(0, 6000))
            assertTrue(!manager.isRenderCapped(0, 1000))
        }
    }

    @Test
    fun searchIsCaseInsensitiveByDefault() = runBlocking {
        PdfDocumentManager.open(pdf("geom-case.pdf")).use { manager ->
            val hits = manager.searchDocument("SEARCH TARGET").toList().flatMap { it.hits }
            assertEquals(2, hits.size)
        }
    }
}
