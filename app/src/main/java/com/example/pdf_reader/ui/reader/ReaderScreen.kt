package com.example.pdf_reader.ui.reader

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.R
import com.example.pdf_reader.data.LibraryRepository
import com.example.pdf_reader.data.ThumbnailStore
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.ui.pdf.PdfViewer
import com.example.pdf_reader.ui.pdf.ReadingMode
import com.example.pdf_reader.ui.pdf.search.PdfSearchBar
import com.example.pdf_reader.ui.pdf.search.rememberSearchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

private sealed interface ReaderState {
    data object Loading : ReaderState
    data class Open(val manager: PdfDocumentManager, val initialPage: Int) : ReaderState
    data class Failed(val error: Throwable) : ReaderState
}

@Composable
fun ReaderScreen(
    uri: Uri,
    hash: String,
    displayName: String,
    repository: LibraryRepository,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ReaderState>(ReaderState.Loading) }

    LaunchedEffect(uri, hash) {
        state = ReaderState.Loading
        state = try {
            val manager = PdfDocumentManager.open(context, uri)
            val stored = repository.get(hash)
            val pageCount = manager.pageCount.value
            val initial = stored?.lastPageIndex?.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) ?: 0
            onPageChanged(initial)
            ReaderState.Open(manager, initial)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ReaderState.Failed(e)
        }
    }

    val manager = (state as? ReaderState.Open)?.manager
    DisposableEffect(manager) {
        onDispose { manager?.close() }
    }

    val open = state as? ReaderState.Open
    LaunchedEffect(open?.manager, hash) {
        val current = open?.manager ?: return@LaunchedEffect
        val pageCount = current.pageCount.value
        repository.updatePageCount(hash, pageCount)
        if (pageCount <= 0) return@LaunchedEffect
        runCatching {
            val image = current.renderPage(0, ThumbnailStore.renderWidthPx)
            val file = ThumbnailStore.file(context, hash)
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out ->
                    image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
                }
            }
            repository.updatePreview(hash, pageCount)
        }
    }

    val search = manager?.let { rememberSearchState(it) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var readingMode by rememberSaveable { mutableStateOf(ReadingMode.Vertical) }
    var pageIndex by remember(hash) { mutableIntStateOf(0) }

    val emptyCount = remember { MutableStateFlow(0) }
    val pageCount by (manager?.pageCount ?: emptyCount).collectAsState()

    Column(modifier.fillMaxSize()) {
        ReaderTopBar(
            displayName = displayName,
            pageIndex = pageIndex,
            pageCount = pageCount,
            mode = readingMode,
            onModeChange = { readingMode = it },
            searchOpen = searchOpen,
            onToggleSearch = { searchOpen = !searchOpen },
            onBack = onBack,
            modifier = Modifier.fillMaxWidth(),
        )

        if (search != null && searchOpen) {
            PdfSearchBar(
                search = search,
                onClose = {
                    search.clear()
                    searchOpen = false
                },
            )
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val current = state) {
                ReaderState.Loading -> CircularProgressIndicator()
                is ReaderState.Failed -> Text(
                    text = stringResource(R.string.reader_cannot_open, current.error.message.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                is ReaderState.Open -> {
                    val count by current.manager.pageCount.collectAsState()
                    var seeded by remember(hash) { mutableStateOf(false) }
                    if (!seeded) {
                        pageIndex = current.initialPage
                        seeded = true
                    }

                    val goToPage = { page: Int ->
                        pageIndex = page
                        onPageChanged(page)
                    }

                    if (readingMode == ReadingMode.SinglePage) {
                        PdfViewer(
                            current.manager,
                            Modifier.fillMaxSize(),
                            mode = readingMode,
                            pageIndex = pageIndex,
                            onPageIndexChange = goToPage,
                            search = search,
                        )
                    } else {
                        val start = pageIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
                        key(readingMode) {
                            val listState = rememberLazyListState(
                                initialFirstVisibleItemIndex = start,
                            )
                            PdfViewer(
                                current.manager,
                                Modifier.fillMaxSize(),
                                mode = readingMode,
                                state = listState,
                                pageIndex = pageIndex,
                                onPageIndexChange = goToPage,
                                search = search,
                            )
                            LaunchedEffect(listState) {
                                snapshotFlow { listState.layoutInfo.viewportEndOffset > 0 }
                                    .first { it }
                                if (listState.firstVisibleItemIndex != start) {
                                    listState.scrollToItem(start)
                                }
                                onPageChanged(start)
                                snapshotFlow { listState.firstVisibleItemIndex }
                                    .distinctUntilChanged()
                                    .collect { page ->
                                        pageIndex = page
                                        onPageChanged(page)
                                    }
                            }
                        }
                    }

                    LaunchedEffect(hash) {
                        snapshotFlow { pageIndex }
                            .distinctUntilChanged()
                            .debounce(400)
                            .collect { repository.updateLastPage(hash, it) }
                    }
                }
            }
        }
    }
}
