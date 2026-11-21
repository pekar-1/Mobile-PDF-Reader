package com.example.pdf_reader.ui.pdf.search

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.pdfium.SearchFlags
import com.example.pdf_reader.pdfium.SearchHit
import com.example.pdf_reader.ui.pdf.overlay.PageOverlay
import com.example.pdf_reader.ui.pdf.overlay.fillNormRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// New query cancels the old search. Polish letters are not ignored (a != ą).
@Stable
class SearchState internal constructor(
    private val manager: PdfDocumentManager,
    private val scope: CoroutineScope,
) {
    var query by mutableStateOf("")
        private set
    var flags by mutableIntStateOf(SearchFlags.NONE)
        private set
    var results by mutableStateOf(SearchResults.EMPTY)
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var isSearching by mutableStateOf(false)
        private set

    // +1 on next/prev, viewer scrolls when it changes
    var navigationTick by mutableIntStateOf(0)
        private set

    val current: SearchHit? get() = results.hits.getOrNull(currentIndex)

    // Search starts from this page.
    internal var firstVisiblePage: () -> Int = { 0 }

    private var job: Job? = null

    val overlay = PageOverlay { pageIndex ->
        val hits = results.byPage[pageIndex] ?: return@PageOverlay
        val current = current
        for (hit in hits) {
            val color = if (hit == current) CurrentHitColor else HitColor
            hit.rects.forEach { fillNormRect(it, color) }
        }
    }

    fun updateQuery(value: String) {
        if (value == query) return
        query = value
        restart(debounce = true)
    }

    fun toggleFlag(flag: Int) {
        flags = flags xor flag
        restart(debounce = false)
    }

    fun clear() {
        query = ""
        restart(debounce = false)
    }

    fun next() = navigate(results.next(currentIndex))

    fun prev() = navigate(results.prev(currentIndex))

    internal fun onDocumentChanged() = restart(debounce = false)

    private fun navigate(index: Int) {
        if (index < 0) return
        currentIndex = index
        navigationTick++
    }

    private fun restart(debounce: Boolean) {
        job?.cancel()
        results = SearchResults.EMPTY
        currentIndex = -1
        isSearching = false
        val q = query
        if (q.isBlank()) return
        val f = flags
        val start = firstVisiblePage()
        job = scope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            isSearching = true
            try {
                manager.searchDocument(q, f, start).collect { page ->
                    val previous = current
                    results = results.plus(page)
                    if (previous != null) {
                        currentIndex = results.indexOf(previous)
                    } else if (page.hits.isNotEmpty()) {
                        // Pages arrive from the start page on: the first hit is the nearest one.
                        navigate(results.indexOf(page.hits.minBy { it.charIndex }))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search failed", e) // E.g. the document was closed meanwhile.
            } finally {
                isSearching = false
            }
        }
    }

    private companion object {
        const val TAG = "SearchState"
        const val DEBOUNCE_MS = 300L
        val HitColor = Color(0x66FFD600)
        val CurrentHitColor = Color(0x99FF6D00)
    }
}

@Composable
fun rememberSearchState(manager: PdfDocumentManager): SearchState {
    val scope = rememberCoroutineScope()
    val state = remember(manager, scope) { SearchState(manager, scope) }
    LaunchedEffect(state) {
        manager.version.drop(1).collect { state.onDocumentChanged() }
    }
    return state
}
