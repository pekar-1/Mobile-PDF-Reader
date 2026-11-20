package com.example.pdf_reader.ui.pdf.search

import com.example.pdf_reader.pdfium.PageSearchResult
import com.example.pdf_reader.pdfium.SearchHit

class SearchResults private constructor(
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

    fun indexOf(hit: SearchHit?): Int {
        if (hit == null) return -1
        val i = hits.binarySearch { compareValuesBy(it, hit, SearchHit::pageIndex, SearchHit::charIndex) }
        return if (i >= 0) i else -1
    }

    // next/prev wrap around, -1 when there are no hits
    fun next(index: Int): Int = if (hits.isEmpty()) -1 else (index + 1).mod(hits.size)

    fun prev(index: Int): Int = when {
        hits.isEmpty() -> -1
        index < 0 -> hits.size - 1
        else -> (index - 1).mod(hits.size)
    }

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyMap(), 0, 0)
    }
}
