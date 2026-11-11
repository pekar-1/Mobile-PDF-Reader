package com.example.pdf_reader.ui.pdf.search

import com.example.pdf_reader.pdfium.PageSearchResult
import com.example.pdf_reader.pdfium.SearchHit

/**
 * Immutable, document-ordered search hits, built page by page while a
 * search runs (pages arrive in wrap-around order from the start page).
 */
class SearchResults private constructor(
    /** All hits sorted by page, then char index. */
    val hits: List<SearchHit>,
    val byPage: Map<Int, List<SearchHit>>,
    val pagesScanned: Int,
    val pageCount: Int,
) {
    val isComplete: Boolean get() = pageCount > 0 && pagesScanned >= pageCount

    fun plus(page: PageSearchResult): SearchResults {
        if (page.hits.isEmpty()) return SearchResults(hits, byPage, page.pagesScanned, page.pageCount)
        val merged = ArrayList<SearchHit>(hits.size + page.hits.size)
        val insertAt = hits.indexOfFirst { it.pageIndex > page.pageIndex }.let { if (it < 0) hits.size else it }
        merged.addAll(hits.subList(0, insertAt))
        merged.addAll(page.hits.sortedBy { it.charIndex })
        merged.addAll(hits.subList(insertAt, hits.size))
        return SearchResults(merged, byPage + (page.pageIndex to page.hits), page.pagesScanned, page.pageCount)
    }

    /** Index of [hit] in [hits], or -1. */
    fun indexOf(hit: SearchHit?): Int {
        if (hit == null) return -1
        val i = hits.binarySearch { compareValuesBy(it, hit, SearchHit::pageIndex, SearchHit::charIndex) }
        return if (i >= 0) i else -1
    }

    /** Next index after [index] with wrap-around, or -1 when empty. */
    fun next(index: Int): Int = if (hits.isEmpty()) -1 else (index + 1).mod(hits.size)

    /** Previous index before [index] with wrap-around, or -1 when empty. */
    fun prev(index: Int): Int = when {
        hits.isEmpty() -> -1
        index < 0 -> hits.size - 1
        else -> (index - 1).mod(hits.size)
    }

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyMap(), 0, 0)
    }
}
