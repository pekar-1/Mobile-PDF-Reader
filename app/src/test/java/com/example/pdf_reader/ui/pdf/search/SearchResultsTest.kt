package com.example.pdf_reader.ui.pdf.search

import com.example.pdf_reader.pdfium.PageSearchResult
import com.example.pdf_reader.pdfium.SearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultsTest {

    private fun hit(page: Int, char: Int) = SearchHit(page, char, 3, emptyList())

    private fun page(index: Int, scanned: Int, vararg chars: Int) =
        PageSearchResult(index, chars.map { hit(index, it) }, scanned, 4)

    @Test
    fun keepsDocumentOrderWhenPagesArriveWrapped() {
        // Search started at page 2: pages arrive 2, 3, 0, 1.
        val results = SearchResults.EMPTY
            .plus(page(2, 1, 50, 10))
            .plus(page(3, 2))
            .plus(page(0, 3, 5))
            .plus(page(1, 4, 7, 1))
        assertEquals(
            listOf(hit(0, 5), hit(1, 1), hit(1, 7), hit(2, 10), hit(2, 50)),
            results.hits,
        )
        assertTrue(results.isComplete)
        assertEquals(2, results.byPage[1]?.size)
    }

    @Test
    fun indexOfFindsHitAfterInsertions() {
        var results = SearchResults.EMPTY.plus(page(2, 1, 10))
        val current = results.hits.single()
        assertEquals(0, results.indexOf(current))
        results = results.plus(page(0, 2, 1, 2))
        assertEquals(2, results.indexOf(current))
        assertEquals(-1, results.indexOf(hit(3, 0)))
        assertFalse(results.isComplete)
    }

    @Test
    fun navigationWraps() {
        val results = SearchResults.EMPTY.plus(page(0, 1, 1, 2, 3))
        assertEquals(1, results.next(0))
        assertEquals(0, results.next(2))
        assertEquals(2, results.prev(0))
        assertEquals(2, results.prev(-1))
        assertEquals(-1, SearchResults.EMPTY.next(0))
    }
}
