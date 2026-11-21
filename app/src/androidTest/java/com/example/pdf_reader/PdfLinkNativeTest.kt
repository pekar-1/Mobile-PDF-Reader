package com.example.pdf_reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.pdfium.PdfLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Link hit-testing through LinkNative: link annotations (URI and GoTo) and
 * plain-text web links. Pages are 612x792 pt (see [TestPdfs]).
 */
@RunWith(AndroidJUnit4::class)
class PdfLinkNativeTest {

    private val cacheDir: File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    // Hex string, so non-ASCII URI bytes stay byte-exact in an ASCII file.
    private fun hex(bytes: ByteArray) = bytes.joinToString("", "<", ">") { "%02X".format(it) }

    private fun uriLink(rect: String, uriBytes: ByteArray) =
        "<< /Type /Annot /Subtype /Link /Rect [$rect] /Border [0 0 0] " +
            "/A << /S /URI /URI ${hex(uriBytes)} >> >>"

    // Rects are [left bottom right top] in points.
    private val asciiRect = "100 100 200 150"
    private val unicodeRect = "300 100 400 150"
    private val gotoRect = "100 300 200 350"
    private val gotoActionRect = "300 300 400 350"

    private fun linksPdf(): File {
        val page0 = listOf(
            uriLink(asciiRect, "https://example.com/a?b=1".toByteArray()),
            uriLink(unicodeRect, "https://pl.wikipedia.org/wiki/Łódź x".toByteArray(Charsets.UTF_8)),
            "<< /Type /Annot /Subtype /Link /Rect [$gotoRect] /Border [0 0 0] /Dest [8 0 R /Fit] >>",
            "<< /Type /Annot /Subtype /Link /Rect [$gotoActionRect] /Border [0 0 0] " +
                "/A << /S /GoTo /D [6 0 R /Fit] >> >>",
        )
        val pages = listOf("Visit www.example.org today", "Second", "Third")
        return File(cacheDir, "links.pdf").apply {
            writeBytes(TestPdfs.build(pages = pages, annots = mapOf(0 to page0)))
        }
    }

    // Center of a "l b r t" rect as normalized (top-left origin) coordinates.
    private fun center(rect: String): Pair<Float, Float> {
        val (l, b, r, t) = rect.split(" ").map { it.toFloat() }
        return ((l + r) / 2 / 612f) to (1f - (b + t) / 2 / 792f)
    }

    private suspend fun linkAt(manager: PdfDocumentManager, rect: String): PdfLink? {
        val (nx, ny) = center(rect)
        return manager.linkAt(0, nx, ny)
    }

    @Test
    fun asciiUriLinkIsReturnedAsIs() = runBlocking {
        PdfDocumentManager.open(linksPdf()).use { manager ->
            assertEquals(PdfLink.Uri("https://example.com/a?b=1"), linkAt(manager, asciiRect))
        }
    }

    @Test
    fun nonAsciiUriLinkIsPercentEncoded() = runBlocking {
        PdfDocumentManager.open(linksPdf()).use { manager ->
            assertEquals(
                PdfLink.Uri("https://pl.wikipedia.org/wiki/%C5%81%C3%B3d%C5%BA%20x"),
                linkAt(manager, unicodeRect),
            )
        }
    }

    @Test
    fun destAndGoToActionPointToPages() = runBlocking {
        PdfDocumentManager.open(linksPdf()).use { manager ->
            assertEquals(PdfLink.Page(2), linkAt(manager, gotoRect))
            assertEquals(PdfLink.Page(1), linkAt(manager, gotoActionRect))
        }
    }

    @Test
    fun plainTextUrlIsAWebLink() = runBlocking {
        PdfDocumentManager.open(linksPdf()).use { manager ->
            // "Visit www.example.org today" at (72, 700), Helvetica 24: the URL starts near x = 135.
            val link = manager.linkAt(0, 200f / 612f, 1f - 708f / 792f)
            assertTrue("Got $link", link is PdfLink.Uri && "www.example.org" in link.uri)
        }
    }

    @Test
    fun emptySpotHasNoLink() = runBlocking {
        PdfDocumentManager.open(linksPdf()).use { manager ->
            assertNull(manager.linkAt(0, 0.95f, 0.95f))
        }
    }
}
