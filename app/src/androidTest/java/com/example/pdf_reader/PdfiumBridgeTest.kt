package com.example.pdf_reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdf_reader.pdfium.PdfDocument
import com.example.pdf_reader.pdfium.SaveFlags
import com.example.pdf_reader.pdfium.SearchFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfiumBridgeTest {

    private lateinit var cacheDir: File
    private lateinit var sample: File

    @Before
    fun setUp() {
        cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        sample = File(cacheDir, "sample.pdf").apply { writeBytes(TestPdfs.build()) }
    }

    @Test
    fun readsTextAndSearches() {
        PdfDocument.open(sample).use { doc ->
            assertEquals(3, doc.pageCount)
            doc.openPage(1).use { page ->
                page.openTextPage().use { text ->
                    assertEquals(TestPdfs.PAGES[1], text.text().trim())

                    val box = requireNotNull(text.charBox(0))
                    assertTrue(box.left < box.right)
                    assertTrue(box.bottom < box.top)
                    assertNull(text.charBox(10_000))

                    val matches = text.findAll("search target")
                    assertEquals(1, matches.size)
                    val match = matches.single()
                    assertEquals("search target".length, match.charCount)
                    assertTrue(match.rects.isNotEmpty())
                    val size = page.size
                    match.rects.forEach { r ->
                        assertTrue(r.left >= 0 && r.right <= size.width)
                        assertTrue(r.bottom >= 0 && r.top <= size.height)
                    }

                    assertTrue(text.findAll("SEARCH", SearchFlags.MATCH_CASE).isEmpty())
                    assertEquals(1, text.findAll("SEARCH").size)
                }
            }
        }
    }

    @Test
    fun editsAndSavesNewDocument() {
        val output = File(cacheDir, "edited.pdf")
        PdfDocument.open(sample).use { source ->
            PdfDocument.createNew().use { doc ->
                assertEquals(0, doc.pageCount)
                doc.importPages(source, "1-2", 0)
                assertEquals(2, doc.pageCount)

                doc.openPage(0).use { it.rotation = 1 }
                doc.deletePage(1)
                assertEquals(1, doc.pageCount)

                doc.saveTo(output, SaveFlags.NO_INCREMENTAL)
            }
        }

        PdfDocument.open(output).use { doc ->
            assertEquals(1, doc.pageCount)
            doc.openPage(0).use { page ->
                assertEquals(1, page.rotation)
                page.openTextPage().use { assertEquals(TestPdfs.PAGES[0], it.text().trim()) }
            }
        }
    }
}
