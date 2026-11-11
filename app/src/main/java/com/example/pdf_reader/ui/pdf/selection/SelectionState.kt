package com.example.pdf_reader.ui.pdf.selection

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.pdf_reader.pdfium.NormRect
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.pdfium.TextPosition
import com.example.pdf_reader.pdfium.TextRange
import com.example.pdf_reader.ui.pdf.overlay.PageOverlay
import com.example.pdf_reader.ui.pdf.overlay.fillNormRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Text selection: a document-ordered `start..end` (inclusive) that may span
 * pages, with its highlight rects and handle boxes.
 *
 * Gesture input arrives far faster than PDFium can hit-test, so requests go
 * through a conflated channel: one worker handles only the latest request.
 * The selection is plain char positions; [ranges] is exactly what a future
 * highlight annotation needs.
 */
@Stable
class SelectionState internal constructor(
    private val manager: PdfDocumentManager,
    private val scope: CoroutineScope,
) {
    var start by mutableStateOf<TextPosition?>(null)
        private set
    var end by mutableStateOf<TextPosition?>(null)
        private set

    /** Normalized rects per page, filled shortly after the selection changes. */
    var rectsByPage by mutableStateOf<Map<Int, List<NormRect>>>(emptyMap())
        private set

    /** Loose boxes (full line height) of the first and last selected char, for handles. */
    var startBox by mutableStateOf<NormRect?>(null)
        private set
    var endBox by mutableStateOf<NormRect?>(null)
        private set

    /** True while a long-press drag or a handle drag is in progress. */
    var isDragging by mutableStateOf(false)
        internal set

    val hasSelection: Boolean get() = start != null && end != null

    /** Fixed part of the selection during a drag: the long-pressed word, or the other handle. */
    private var fixed: Pair<TextPosition, TextPosition>? = null

    private val requests = Channel<Request>(Channel.CONFLATED)

    val overlay = PageOverlay { pageIndex ->
        rectsByPage[pageIndex]?.forEach { fillNormRect(it, SelectionColor) }
    }

    init {
        scope.launch {
            for (request in requests) {
                try {
                    handle(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Selection request failed", e) // E.g. document closed.
                }
            }
        }
    }

    /** Long press at a normalized point: selects the word there. */
    internal fun selectWordAt(pageIndex: Int, nx: Float, ny: Float) {
        requests.trySend(Request.Word(pageIndex, nx, ny))
    }

    /** Drag after a long press, or a handle drag: grows the selection from the fixed part. */
    internal fun extendTo(pageIndex: Int, nx: Float, ny: Float) {
        requests.trySend(Request.Extend(pageIndex, nx, ny))
    }

    /** Starts dragging a handle; the opposite end stays fixed. */
    internal fun beginHandleDrag(isStart: Boolean) {
        val s = start ?: return
        val e = end ?: return
        fixed = if (isStart) e to e else s to s
        isDragging = true
    }

    internal fun endDrag() {
        isDragging = false
    }

    /** Selects all text of the page where the selection starts. */
    fun selectAllOnPage() {
        val page = start?.pageIndex ?: return
        requests.trySend(Request.Page(page))
    }

    fun clear() {
        requests.trySend(Request.Clear)
    }

    /** Per-page char ranges of the current selection. */
    suspend fun ranges(): List<TextRange> {
        val s = start ?: return emptyList()
        val e = end ?: return emptyList()
        val counts = (s.pageIndex..e.pageIndex).associateWith { manager.charCount(it) }
        return SelectionMath.ranges(s, e) { counts.getValue(it) }
    }

    /** Selected text; pages are joined by line breaks. PDFium's `\r\n` become `\n`. */
    suspend fun text(): String = manager.text(ranges()).replace("\r\n", "\n")

    internal fun onDocumentChanged() = clear()

    // ------------------------------------------------------------------ worker

    private suspend fun handle(request: Request) {
        when (request) {
            Request.Clear -> set(null, null)
            is Request.Page -> {
                val count = manager.charCount(request.pageIndex)
                if (count > 0) set(TextPosition(request.pageIndex, 0), TextPosition(request.pageIndex, count - 1))
            }
            is Request.Word -> selectWord(request.pageIndex, request.nx, request.ny)
            is Request.Extend -> {
                val anchor = fixed
                if (anchor == null) {
                    // The long-press request was conflated away; treat this as the press.
                    selectWord(request.pageIndex, request.nx, request.ny)
                    return
                }
                val target = manager.charAt(request.pageIndex, request.nx, request.ny, DRAG_TOLERANCE_PT)
                    ?: return // Between lines: keep the last selection.
                val (s, e) = SelectionMath.extend(anchor, target)
                set(s, e)
            }
        }
    }

    private suspend fun selectWord(pageIndex: Int, nx: Float, ny: Float) {
        val word = manager.wordAt(pageIndex, nx, ny)
        if (word == null || word.isEmpty) {
            fixed = null
            return
        }
        val bounds = SelectionMath.bounds(word)
        fixed = bounds
        set(bounds.first, bounds.second)
    }

    private suspend fun set(s: TextPosition?, e: TextPosition?) {
        if (s == start && e == end) return
        if (s == null || e == null) {
            fixed = null
            start = null
            end = null
            rectsByPage = emptyMap()
            startBox = null
            endBox = null
            return
        }
        // Compute everything first, then publish at once: no half-updated frame.
        val counts = (s.pageIndex..e.pageIndex).associateWith { manager.charCount(it) }
        val rects = SelectionMath.ranges(s, e) { counts.getValue(it) }
            .associate { it.pageIndex to manager.rects(it) }
        val sBox = manager.charBox(s)
        val eBox = manager.charBox(e)
        start = s
        end = e
        rectsByPage = rects
        startBox = sBox
        endBox = eBox
    }

    private sealed interface Request {
        data class Word(val pageIndex: Int, val nx: Float, val ny: Float) : Request
        data class Extend(val pageIndex: Int, val nx: Float, val ny: Float) : Request
        data class Page(val pageIndex: Int) : Request
        data object Clear : Request
    }

    private companion object {
        const val TAG = "SelectionState"

        /** Wider than a tap: while dragging, the finger hides the text and drifts between lines. */
        const val DRAG_TOLERANCE_PT = 10.0
        val SelectionColor = Color(0x553D7BFF)
    }
}

/** Selection bound to [manager]; cleared after every document edit. */
@Composable
fun rememberSelectionState(manager: PdfDocumentManager): SelectionState {
    val scope = rememberCoroutineScope()
    val state = remember(manager, scope) { SelectionState(manager, scope) }
    LaunchedEffect(state) {
        manager.version.drop(1).collect { state.onDocumentChanged() }
    }
    return state
}
