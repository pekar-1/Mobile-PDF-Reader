package com.example.pdf_reader.pdfium

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.pdf_reader.pdfium.jni.CoreNative
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Owns one PDF document and is the only entry point the UI should use.
 *
 * Threading: every PDFium call runs on a private single-thread dispatcher
 * ("document thread"), so file access is strictly sequential and in FIFO
 * order. All public functions are `suspend` and safe to call from any thread.
 *
 * Rendering returns [ImageBitmap] for Compose. Abandoned renders are dropped:
 * - still queued: skipped without allocating a bitmap,
 * - already running: PDFium is told to stop via a native cancel token,
 * - shared: identical requests share one render, which is cancelled only
 *   when no caller waits for it anymore.
 *
 * Page window: [setViewport] prefetches the next pages in the background
 * (only while no visible render is pending) and pins the previous ones in
 * memory, so scrolling either way shows ready bitmaps.
 *
 * Call [close] when done (e.g. from `DisposableEffect.onDispose`).
 */
class PdfDocumentManager private constructor(
    private val document: PdfDocument,
    private val executor: ExecutorService,
    private val dispatcher: ExecutorCoroutineDispatcher,
    private val documentThread: Thread,
    cacheMaxBytes: Int,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val cache = PdfBitmapCache(cacheMaxBytes)

    // Guarded by inFlightLock.
    private val inFlight = HashMap<RenderKey, InFlightRender>()
    private val inFlightLock = Any()

    /** Number of in-flight renders a visible page waits for. Prefetch runs only at 0. */
    private val visibleDemand = MutableStateFlow(0)

    @Volatile
    private var viewport: Viewport? = null

    // Guarded by prefetchLock.
    private val prefetchJobs = HashMap<RenderKey, Job>()
    private val prefetchLock = Any()

    /** One prefetch at a time, in launch order (Mutex is fair). */
    private val prefetchMutex = Mutex()

    // Document thread only.
    private val pagePool = object : LinkedHashMap<Int, PooledPage>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PooledPage>): Boolean {
            if (size <= PAGE_POOL_SIZE) return false
            eldest.value.close()
            return true
        }
    }

    private val _pageCount = MutableStateFlow(0)
    private val _pageSizes = MutableStateFlow<List<PdfSize>>(emptyList())
    private val _version = MutableStateFlow(0)

    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    /** Page sizes in PDF points (rotation applied), for layout before rendering. */
    val pageSizes: StateFlow<List<PdfSize>> = _pageSizes.asStateFlow()

    /** Incremented after every edit; rendered bitmaps of older versions are stale. */
    val version: StateFlow<Int> = _version.asStateFlow()

    val isClosed: Boolean get() = closed.get()

    internal val stats = RenderStats()

    // ---------------------------------------------------------------- render

    /**
     * Renders page [index] at [widthPx] wide (height from the page aspect
     * ratio). Cancelling the calling coroutine abandons the render.
     */
    suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap =
        renderBitmap(index, widthPx).asImageBitmap()

    internal suspend fun renderBitmap(index: Int, widthPx: Int): Bitmap {
        require(widthPx > 0) { "widthPx must be > 0" }
        repeat(MAX_STALE_RETRIES) {
            ensureOpen()
            val key = RenderKey(_version.value, index, widthPx)
            cache[key]?.let {
                stats.cacheHits.incrementAndGet()
                return it
            }
            awaitShared(key)?.let { return it }
            // null: an edit made the request stale before it ran; retry.
        }
        throw CancellationException("Document keeps changing; render abandoned")
    }

    /**
     * `true` when a full render of page [index] at [widthPx] is downscaled by
     * the bitmap limits, so zoomed-in views need [renderRegion] to stay sharp.
     */
    fun isRenderCapped(index: Int, widthPx: Int): Boolean {
        val size = _pageSizes.value.getOrNull(index) ?: return false
        return bitmapSize(widthPx, size).first < widthPx
    }

    /**
     * Renders only [region] of page [index], laid out [pageWidthPx] wide, at
     * full resolution (for zoom beyond the full-page bitmap limits). Not
     * cached. Cancelling the caller stops the native render.
     */
    suspend fun renderRegion(index: Int, pageWidthPx: Int, region: android.graphics.Rect): ImageBitmap {
        require(pageWidthPx > 0) { "pageWidthPx must be > 0" }
        require(!region.isEmpty) { "region must not be empty" }
        require(region.width() <= MAX_BITMAP_SIDE && region.height() <= MAX_BITMAP_SIDE) {
            "region larger than $MAX_BITMAP_SIDE px"
        }
        val token = PdfRenderCancelToken()
        try {
            return coroutineScope {
                // Cancels the native render when the caller is cancelled.
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        token.cancel()
                    }
                }
                try {
                    // withContext returns only after the block has finished,
                    // so closing the token afterwards is safe.
                    onDocumentThread {
                        val size = _pageSizes.value.getOrNull(index)
                            ?: throw IllegalArgumentException("Page index $index out of range")
                        val pageHeightPx = (pageWidthPx * size.height / size.width).roundToInt()
                        val bitmap = Bitmap.createBitmap(region.width(), region.height(), Bitmap.Config.ARGB_8888)
                        when (
                            pooledPage(index).renderRegionInto(
                                bitmap, pageWidthPx, pageHeightPx, region.left, region.top, token,
                            )
                        ) {
                            PdfRenderResult.DONE -> bitmap.also { it.prepareToDraw() }.asImageBitmap()
                            PdfRenderResult.CANCELLED -> throw CancellationException("Region render abandoned")
                            PdfRenderResult.FAILED ->
                                throw PdfiumException("Cannot render page $index", CoreNative.nativeGetLastError())
                        }
                    }
                } finally {
                    watcher.cancel()
                }
            }
        } finally {
            token.close()
        }
    }

    /** Cached bitmap of page [index] at [widthPx] for the current version, without queueing. */
    fun cachedPage(index: Int, widthPx: Int): ImageBitmap? {
        if (closed.get()) return null
        return cache[RenderKey(_version.value, index, widthPx)]?.asImageBitmap()
    }

    // --------------------------------------------------------------- viewport

    /**
     * Reports the visible pages [firstVisible]..[lastVisible] rendered at
     * [widthPx]. The next [PREFETCH_AHEAD] pages are rendered in the background
     * (after visible renders), and the previous [KEEP_BEHIND] pages stay in
     * memory if already rendered. Safe from any thread; ignored after [close].
     */
    fun setViewport(firstVisible: Int, lastVisible: Int, widthPx: Int) {
        require(widthPx > 0) { "widthPx must be > 0" }
        if (closed.get()) return
        viewport = Viewport(firstVisible, lastVisible, widthPx)
        applyViewport()
    }

    private fun applyViewport() {
        val vp = viewport ?: return
        synchronized(prefetchLock) {
            if (closed.get()) return
            val version = _version.value
            val count = _pageCount.value
            val first = vp.first.coerceAtLeast(0)
            val last = vp.last.coerceAtMost(count - 1)
            fun key(index: Int) = RenderKey(version, index, vp.widthPx)

            val ahead = if (first > last) emptyList()
            else ((last + 1)..minOf(last + PREFETCH_AHEAD, count - 1)).map(::key)
            val window = if (first > last) emptySet()
            else (maxOf(first - KEEP_BEHIND, 0)..minOf(last + PREFETCH_AHEAD, count - 1)).mapTo(HashSet(), ::key)
            cache.pin(window)

            // Keep prefetches still inside the window; their renders go on.
            val iterator = prefetchJobs.entries.iterator()
            while (iterator.hasNext()) {
                val (key, job) = iterator.next()
                if (key !in ahead) {
                    job.cancel()
                    iterator.remove()
                }
            }
            for (key in ahead) {
                if (key in prefetchJobs || cache[key] != null) continue
                val job = scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) { prefetch(key) }
                prefetchJobs[key] = job
                job.invokeOnCompletion {
                    synchronized(prefetchLock) { if (prefetchJobs[key] === job) prefetchJobs.remove(key) }
                }
                job.start()
            }
        }
    }

    private suspend fun prefetch(key: RenderKey) {
        try {
            prefetchMutex.withLock {
                visibleDemand.first { it == 0 } // Visible pages first.
                if (cache[key] != null) return
                stats.prefetchStarted.incrementAndGet()
                if (awaitShared(key, prefetch = true) != null) stats.prefetchCompleted.incrementAndGet()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never crash the app from a background prefetch; the visible request reports errors.
            if (!closed.get()) Log.w(TAG, "Prefetch of page ${key.pageIndex} failed", e)
        }
    }

    private suspend fun awaitShared(key: RenderKey, prefetch: Boolean = false): Bitmap? {
        val entry = synchronized(inFlightLock) {
            inFlight.getOrPut(key) { startRender(key) }.also {
                it.waiters++
                if (!prefetch && it.priorityWaiters++ == 0) visibleDemand.value += 1
            }
        }
        try {
            return entry.deferred.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive() // Caller cancelled: rethrow as is.
            ensureOpen() // Closed while waiting: IllegalStateException.
            throw e
        } finally {
            synchronized(inFlightLock) {
                entry.waiters--
                if (!prefetch && --entry.priorityWaiters == 0) visibleDemand.value -= 1
                if (entry.waiters == 0) {
                    if (inFlight[key] === entry) inFlight.remove(key)
                    if (!entry.deferred.isCompleted) entry.cancel()
                }
            }
        }
    }

    // Called under inFlightLock.
    private fun startRender(key: RenderKey): InFlightRender {
        val token = PdfRenderCancelToken()
        val entry = InFlightRender(token)
        entry.deferred = scope.async { renderOnDocumentThread(key, entry) }
        entry.deferred.invokeOnCompletion { cause ->
            if (cause is CancellationException && !entry.started) {
                stats.cancelledQueued.incrementAndGet()
            }
            token.close() // Body has returned (or never ran): safe to free.
        }
        return entry
    }

    /** Runs on the document thread. Returns null when the request is stale. */
    private fun CoroutineScope.renderOnDocumentThread(key: RenderKey, entry: InFlightRender): Bitmap? {
        ensureActive()
        ensureOpen()
        checkDocumentThread()
        if (key.version != _version.value) return null
        cache[key]?.let { return it }

        entry.started = true
        stats.started.incrementAndGet()

        val pageSize = _pageSizes.value.getOrNull(key.pageIndex)
            ?: throw IllegalArgumentException("Page index ${key.pageIndex} out of range")
        val (width, height) = bitmapSize(key.widthPx, pageSize)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        return when (pooledPage(key.pageIndex).renderInto(bitmap, entry.token)) {
            PdfRenderResult.DONE -> {
                stats.completed.incrementAndGet()
                bitmap.prepareToDraw() // Start GPU upload off the main thread.
                cache.put(key, bitmap)
                bitmap
            }
            PdfRenderResult.CANCELLED -> {
                stats.cancelledNative.incrementAndGet()
                throw CancellationException("Render of page ${key.pageIndex} abandoned")
            }
            PdfRenderResult.FAILED ->
                throw PdfiumException("Cannot render page ${key.pageIndex}", CoreNative.nativeGetLastError())
        }
    }

    // ------------------------------------------------------------ text/search

    /** Whole text of page [index]. */
    suspend fun text(index: Int): String = onDocumentThread { pooled(index).text() }

    /** All matches of [query] on page [index]. [flags]: see [SearchFlags]. */
    suspend fun search(index: Int, query: String, flags: Int = SearchFlags.NONE): List<PdfSearchMatch> =
        onDocumentThread { pooled(index).textPage().findAll(query, flags) }

    /**
     * Char under normalized point ([nx], [ny]) on page [pageIndex], or `null`
     * when no char is within [tolerancePt] points.
     */
    suspend fun charAt(
        pageIndex: Int,
        nx: Float,
        ny: Float,
        tolerancePt: Double = HIT_TOLERANCE_PT,
    ): TextPosition? = onDocumentThread {
        val pooled = pooled(pageIndex)
        val (x, y) = pooled.page.normToPage(nx.toDouble(), ny.toDouble())
        pooled.textPage().charIndexAt(x, y, tolerancePt, tolerancePt)?.let { TextPosition(pageIndex, it) }
    }

    /**
     * Word under normalized point ([nx], [ny]) on page [pageIndex]. Falls back
     * to the single char when it is not part of a word (space, punctuation).
     */
    suspend fun wordAt(
        pageIndex: Int,
        nx: Float,
        ny: Float,
        tolerancePt: Double = HIT_TOLERANCE_PT,
    ): TextRange? = onDocumentThread {
        val pooled = pooled(pageIndex)
        val (x, y) = pooled.page.normToPage(nx.toDouble(), ny.toDouble())
        val index = pooled.textPage().charIndexAt(x, y, tolerancePt, tolerancePt) ?: return@onDocumentThread null
        TextBoundaries.wordAround(pooled.text(), index)?.let { (start, end) -> TextRange(pageIndex, start, end) }
    }

    /** Number of chars on page [pageIndex] (includes generated line breaks). */
    suspend fun charCount(pageIndex: Int): Int = onDocumentThread { pooled(pageIndex).textPage().charCount }

    /** Normalized rectangles covering [range], one per text run. */
    suspend fun rects(range: TextRange): List<NormRect> = onDocumentThread {
        if (range.isEmpty) return@onDocumentThread emptyList()
        val pooled = pooled(range.pageIndex)
        pooled.page.toNorm(pooled.textPage().rects(range.start, range.count))
    }

    /**
     * Normalized loose box of one char (stable line height), e.g. for
     * selection handles, or `null` for an invalid position.
     */
    suspend fun charBox(position: TextPosition): NormRect? = onDocumentThread {
        val pooled = pooled(position.pageIndex)
        val box = pooled.textPage().looseCharBox(position.charIndex) ?: return@onDocumentThread null
        pooled.page.toNorm(listOf(box)).firstOrNull()
    }

    /** Text of [ranges] in order; ranges on different pages are joined by a line break. */
    suspend fun text(ranges: List<TextRange>): String = onDocumentThread {
        buildString {
            var lastPage = -1
            for (range in ranges) {
                if (range.isEmpty) continue
                if (lastPage != -1 && range.pageIndex != lastPage && lastOrNull() != '\n') append('\n')
                append(pooled(range.pageIndex).textPage().text(range.start, range.count))
                lastPage = range.pageIndex
            }
        }
    }

    /**
     * Searches the whole document, page by page, starting at [startPage] and
     * wrapping around. Emits one [PageSearchResult] per scanned page (also
     * without hits, for progress). Each page is a separate document-thread
     * task, so renders keep running in between. Collection stops early when
     * the document is edited, since hits of an older version are stale.
     */
    fun searchDocument(query: String, flags: Int = SearchFlags.NONE, startPage: Int = 0): Flow<PageSearchResult> =
        flow {
            require(query.isNotEmpty()) { "Query must not be empty" }
            val version = _version.value
            val count = _pageCount.value
            if (count == 0) return@flow
            val first = startPage.coerceIn(0, count - 1)
            for (step in 0 until count) {
                val pageIndex = (first + step) % count
                val hits = onDocumentThread {
                    if (_version.value != version) return@onDocumentThread null
                    val pooled = pooled(pageIndex)
                    pooled.textPage().findAll(query, flags).map { match ->
                        SearchHit(pageIndex, match.charIndex, match.charCount, pooled.page.toNorm(match.rects))
                    }
                } ?: return@flow
                emit(PageSearchResult(pageIndex, hits, step + 1, count))
            }
        }

    // ------------------------------------------------------------------ edits

    /** [rotation]: 0..3 = 0°, 90°, 180°, 270° clockwise. */
    suspend fun setRotation(index: Int, rotation: Int) = edit {
        pooledPage(index).rotation = rotation
    }

    suspend fun deletePage(index: Int) = edit {
        closePooledPages()
        document.deletePage(index)
    }

    /**
     * Copies pages from [source] (1-based [pageRange], `null` = all) at
     * [insertIndex]. Keep [source] open until this document is saved.
     */
    suspend fun importPages(source: PdfDocumentManager, pageRange: String? = null, insertIndex: Int? = null) {
        require(source !== this) { "Cannot import from the same document" }
        source.ensureOpen()
        edit {
            closePooledPages()
            // Native calls are additionally serialized by the global PDFium lock,
            // and importing only reads the source document.
            document.importPages(source.document, pageRange, insertIndex ?: document.pageCount)
        }
    }

    suspend fun saveTo(file: File, flags: Int = SaveFlags.NONE) = onDocumentThread {
        document.saveTo(file, flags)
    }

    /** Saves to a writable [uri] (e.g. from `CreateDocument`). The target is truncated. */
    suspend fun saveTo(context: Context, uri: Uri, flags: Int = SaveFlags.NONE) = onDocumentThread {
        val output = context.contentResolver.openFileDescriptor(uri, "wt")
            ?: throw FileNotFoundException("Cannot open $uri for writing")
        output.use { document.saveTo(it, flags) }
    }

    // -------------------------------------------------------------- lifecycle

    /**
     * Abandons all renders and closes the document on its thread. Safe to call
     * from any thread, more than once. Later calls throw IllegalStateException.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(inFlightLock) {
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
        }
        synchronized(prefetchLock) {
            prefetchJobs.values.forEach { it.cancel() }
            prefetchJobs.clear()
        }
        scope.cancel()
        // Queued after every task submitted so far: nothing touches the
        // document after this runs (queued tasks see `closed` and bail out).
        executor.execute {
            closePooledPages()
            document.close()
            cache.evictAll()
            Log.d(TAG, "Closed. $stats")
        }
        executor.shutdown()
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun <T> onDocumentThread(block: () -> T): T {
        ensureOpen()
        return withContext(dispatcher) {
            ensureOpen()
            block()
        }
    }

    private suspend fun edit(block: () -> Unit) = onDocumentThread {
        block()
        refreshState()
    }

    /** Document thread only. Re-reads page metadata and invalidates renders. */
    private fun refreshState() {
        checkDocumentThread()
        val count = document.pageCount
        _pageSizes.value = List(count) { document.pageSize(it) }
        _pageCount.value = count
        cache.evictAll()
        pagePool.values.forEach { it.dropText() } // Edits may change the text layer.
        _version.value += 1
        applyViewport() // Re-pin and re-prefetch for the new version.
    }

    private fun pooledPage(index: Int): PdfPage = pooled(index).page

    private fun pooled(index: Int): PooledPage {
        checkDocumentThread()
        return pagePool.getOrPut(index) { PooledPage(document.openPage(index)) }
    }

    private fun closePooledPages() {
        checkDocumentThread()
        pagePool.values.forEach { it.close() }
        pagePool.clear()
    }

    private fun ensureOpen() {
        check(!closed.get()) { "PdfDocumentManager is closed" }
    }

    private fun checkDocumentThread() {
        check(Thread.currentThread() === documentThread) {
            "PDFium accessed outside the document thread"
        }
    }

    /**
     * Pooled page with its lazily loaded text layer and text. Document thread
     * only. Closing the page also closes the text page (see [PdfPage.close]).
     */
    private class PooledPage(val page: PdfPage) {
        private var textPage: PdfTextPage? = null
        private var text: String? = null

        fun textPage(): PdfTextPage =
            textPage?.takeUnless { it.isClosed } ?: page.openTextPage().also { textPage = it }

        fun text(): String = text ?: textPage().text().also { text = it }

        fun dropText() {
            textPage?.close()
            textPage = null
            text = null
        }

        fun close() = page.close()
    }

    private class InFlightRender(val token: PdfRenderCancelToken) {
        lateinit var deferred: Deferred<Bitmap?>
        var waiters = 0 // Guarded by inFlightLock.
        var priorityWaiters = 0 // Non-prefetch waiters; guarded by inFlightLock.

        @Volatile
        var started = false

        fun cancel() {
            token.cancel() // Stops a running native render.
            deferred.cancel() // Skips a queued one.
        }
    }

    internal class RenderStats {
        val started = AtomicInteger()
        val completed = AtomicInteger()
        val cancelledQueued = AtomicInteger()
        val cancelledNative = AtomicInteger()
        val cacheHits = AtomicInteger()
        val prefetchStarted = AtomicInteger()
        val prefetchCompleted = AtomicInteger()

        override fun toString() =
            "started=$started completed=$completed cancelledQueued=$cancelledQueued " +
                "cancelledNative=$cancelledNative cacheHits=$cacheHits " +
                "prefetchStarted=$prefetchStarted prefetchCompleted=$prefetchCompleted"
    }

    private data class Viewport(val first: Int, val last: Int, val widthPx: Int)

    companion object {
        private const val TAG = "PdfDocumentManager"
        private const val PAGE_POOL_SIZE = 4
        private const val MAX_STALE_RETRIES = 3

        /** Default hit-test tolerance in PDF points (about a finger's slop at 1x). */
        const val HIT_TOLERANCE_PT = 4.0
        const val PREFETCH_AHEAD = 2
        const val KEEP_BEHIND = 2
        private const val MAX_BITMAP_SIDE = 4096
        private const val MAX_BITMAP_BYTES = 32L * 1024 * 1024
        private val threadCounter = AtomicInteger()

        /** Opens a document from any readable [uri] (file:// or content://). */
        suspend fun open(context: Context, uri: Uri, password: String? = null): PdfDocumentManager {
            val resolver = context.applicationContext.contentResolver
            return create {
                val input = resolver.openFileDescriptor(uri, "r")
                    ?: throw FileNotFoundException("Cannot open $uri")
                input.use { PdfDocument.open(it, password) } // fd is dup'ed natively.
            }
        }

        suspend fun open(file: File, password: String? = null): PdfDocumentManager =
            create { PdfDocument.open(file, password) }

        /** Tests: small cache to force LRU eviction. */
        internal suspend fun open(file: File, cacheMaxBytes: Int): PdfDocumentManager =
            create(cacheMaxBytes) { PdfDocument.open(file, null) }

        private suspend fun create(
            cacheMaxBytes: Int = PdfBitmapCache.defaultMaxBytes(),
            opener: () -> PdfDocument,
        ): PdfDocumentManager {
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "pdfium-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
            }
            val dispatcher = executor.asCoroutineDispatcher()
            val manager = try {
                // NonCancellable: never drop an opened document on the floor.
                withContext(dispatcher + NonCancellable) {
                    val document = opener()
                    try {
                        PdfDocumentManager(document, executor, dispatcher, Thread.currentThread(), cacheMaxBytes)
                            .also { it.refreshState() }
                    } catch (t: Throwable) {
                        document.close()
                        throw t
                    }
                }
            } catch (t: Throwable) {
                executor.shutdown()
                throw t
            }
            if (!currentCoroutineContext().isActive) {
                manager.close()
                currentCoroutineContext().ensureActive()
            }
            return manager
        }

        /** Fits the bitmap into side and byte limits, keeping the page aspect ratio. */
        internal fun bitmapSize(widthPx: Int, page: PdfSize): Pair<Int, Int> {
            var width = widthPx.toDouble()
            var height = width * page.height / page.width
            val sideScale = MAX_BITMAP_SIDE / max(width, height)
            if (sideScale < 1) {
                width *= sideScale
                height *= sideScale
            }
            val bytes = width * height * 4
            if (bytes > MAX_BITMAP_BYTES) {
                val byteScale = sqrt(MAX_BITMAP_BYTES / bytes)
                width *= byteScale
                height *= byteScale
            }
            return max(1, width.roundToInt()) to max(1, height.roundToInt())
        }
    }
}
