package com.example.pdf_reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.R
import com.example.pdf_reader.ui.pdf.PdfViewer
import com.example.pdf_reader.ui.pdf.ReadingMode
import com.example.pdf_reader.ui.pdf.search.PdfSearchBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@Composable
internal fun ReaderScreen(
    viewModel: ReaderViewModel,
    displayName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val pageIndex by viewModel.pageIndex.collectAsState()
    val open = state as? ReaderState.Open
    val manager = open?.manager
    val search = open?.search
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var readingMode by rememberSaveable { mutableStateOf(ReadingMode.Vertical) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val density = LocalDensity.current
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val pageCount = manager?.pageCount?.collectAsState()?.value ?: 0
    val chrome = chromeVisible || searchOpen
    val topInset = with(density) { chromeHeightPx.toDp() }

    fun showChrome() {
        chromeVisible = true
    }

    fun toggleChrome() {
        if (searchOpen) return
        chromeVisible = !chromeVisible
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            ReaderState.Loading -> CircularProgressIndicator()
            is ReaderState.Failed -> Text(
                text = stringResource(R.string.reader_cannot_open, current.error.message.orEmpty()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            is ReaderState.Open -> {
                val goToPage = viewModel::onPageChanged

                if (readingMode == ReadingMode.SinglePage) {
                    PdfViewer(
                        current.manager,
                        Modifier.fillMaxSize(),
                        mode = readingMode,
                        pageIndex = pageIndex,
                        onPageIndexChange = goToPage,
                        search = search,
                        onTap = { toggleChrome() },
                        topInset = topInset,
                    )
                } else {
                    val start = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    key(readingMode) {
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = start,
                        )
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.isScrollInProgress }
                                .distinctUntilChanged()
                                .collect { scrolling ->
                                    if (scrolling && !searchOpen) chromeVisible = false
                                }
                        }
                        PdfViewer(
                            current.manager,
                            Modifier.fillMaxSize(),
                            mode = readingMode,
                            state = listState,
                            pageIndex = pageIndex,
                            onPageIndexChange = goToPage,
                            search = search,
                            onTap = { toggleChrome() },
                            topInset = topInset,
                        )
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.layoutInfo.viewportEndOffset > 0 }
                                .first { it }
                            if (listState.firstVisibleItemIndex != start) {
                                listState.scrollToItem(start)
                            }
                            goToPage(start)
                            snapshotFlow { listState.firstVisibleItemIndex }
                                .distinctUntilChanged()
                                .collect { goToPage(it) }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(90)) + slideInVertically(tween(90)) { -it / 3 },
            exit = fadeOut(tween(70)) + slideOutVertically(tween(70)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { chromeHeightPx = it.height },
                ) {
                    ReaderTopBar(
                        displayName = displayName,
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                        mode = readingMode,
                        onModeChange = {
                            readingMode = it
                            showChrome()
                        },
                        searchOpen = searchOpen,
                        onToggleSearch = {
                            searchOpen = !searchOpen
                            if (searchOpen) showChrome()
                        },
                        onBack = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                    )
                }
                if (search != null && searchOpen) {
                    PdfSearchBar(
                        search = search,
                        onClose = {
                            search.clear()
                            searchOpen = false
                        },
                    )
                }
            }
        }
    }
}
