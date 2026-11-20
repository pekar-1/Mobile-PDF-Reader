package com.example.pdf_reader.ui.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect as AndroidRect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.example.pdf_reader.pdfium.PdfLink
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.R
import com.example.pdf_reader.pdfium.NormRect
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.pdfium.SearchHit
import com.example.pdf_reader.ui.pdf.overlay.PageOverlay
import com.example.pdf_reader.ui.pdf.search.SearchState
import com.example.pdf_reader.ui.pdf.selection.SelectionHandles
import com.example.pdf_reader.ui.pdf.selection.SelectionState
import com.example.pdf_reader.ui.pdf.selection.SelectionToolbar
import com.example.pdf_reader.ui.pdf.selection.rememberSelectionState
import com.example.pdf_reader.ui.pdf.selection.selectionGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import com.example.pdf_reader.ui.pdf.zoom.ZoomMath
import com.example.pdf_reader.ui.pdf.zoom.ZoomState
import com.example.pdf_reader.ui.pdf.zoom.rememberZoomState
import com.example.pdf_reader.ui.pdf.zoom.zoomGestures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val PageGap = 8.dp

// Wait after scroll/zoom before rendering the sharp part.
private const val HIGH_RES_DEBOUNCE_MS = 150L

// Not Compose state on purpose (no recomposition on every move).
internal class ViewportRef {
    var coordinates: LayoutCoordinates? = null
}

@Composable
fun PdfViewer(
    manager: PdfDocumentManager,
    modifier: Modifier = Modifier,
    mode: ReadingMode = ReadingMode.Vertical,
    state: LazyListState = rememberLazyListState(),
    pageIndex: Int = 0,
    onPageIndexChange: (Int) -> Unit = {},
    search: SearchState? = null,
    selection: SelectionState? = rememberSelectionState(manager),
    overlays: List<PageOverlay> = emptyList(),
    onTap: () -> Unit = {},
    topInset: Dp = 0.dp,
) {
    val pageCount by manager.pageCount.collectAsState()
    val sizes by manager.pageSizes.collectAsState()
    val scope = rememberCoroutineScope()
    val zoom = rememberZoomState(state, scope)
    val viewport = remember { ViewportRef() }
    val layer = remember { ViewportRef() }
    val registry = remember { PageRegistry() }
    val pageOverlays = remember(search, selection, overlays) {
        listOfNotNull(search?.overlay, selection?.overlay) + overlays
    }

    val autoScroll: (Offset) -> Unit = remember(state, viewport, mode) {
        { window ->
            if (mode == ReadingMode.Vertical) {
                viewport.coordinates?.takeIf { it.isAttached }?.let { coords ->
                    val y = coords.windowToLocal(window).y
                    val height = coords.size.height.toFloat()
                    val edge = height * AUTO_SCROLL_EDGE
                    val delta = when {
                        y < edge -> -(edge - y) * AUTO_SCROLL_SPEED
                        y > height - edge -> (y - (height - edge)) * AUTO_SCROLL_SPEED
                        else -> 0f
                    }
                    if (delta != 0f) state.dispatchRawDelta(delta)
                }
            }
        }
    }

    // Internal link jumps; system back returns to the page the link was tapped on.
    val context = LocalContext.current
    val linkHistory = remember(manager) { mutableStateListOf<Int>() }
    val jumpTo: (Int) -> Unit = { page ->
        val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (mode == ReadingMode.SinglePage) onPageIndexChange(target)
        else scope.launch { state.scrollToItem(target) }
    }
    val onLinkTap: (Int, Float, Float) -> Unit = { page, nx, ny ->
        scope.launch {
            val link = try {
                manager.linkAt(page, nx, ny)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // Page closed or document edited meanwhile: treat as no link.
            }
            when (link) {
                is PdfLink.Page -> {
                    linkHistory += if (mode == ReadingMode.SinglePage) pageIndex else state.firstVisibleItemIndex
                    jumpTo(link.pageIndex)
                }
                is PdfLink.Uri -> openExternalLink(context, link.uri)
                null -> onTap()
            }
        }
    }
    BackHandler(enabled = linkHistory.isNotEmpty()) { jumpTo(linkHistory.removeAt(linkHistory.lastIndex)) }

    if (selection != null) SelectionToolbar(selection, registry, state, zoom)

    if (search != null) {
        search.firstVisiblePage = {
            when (mode) {
                ReadingMode.SinglePage -> pageIndex
                else -> state.firstVisibleItemIndex
            }
        }
        LaunchedEffect(search, state, zoom, mode) {
            snapshotFlow { search.navigationTick }
                .distinctUntilChanged()
                .collect { tick ->
                    if (tick <= 0) return@collect
                    val hit = search.current ?: return@collect
                    if (mode == ReadingMode.SinglePage) {
                        onPageIndexChange(hit.pageIndex)
                    } else {
                        scrollToHit(hit, state, zoom)
                    }
                }
        }
    }

    val zoomable = mode == ReadingMode.Vertical
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .onGloballyPositioned { viewport.coordinates = it }
            .then(if (zoomable) Modifier.zoomGestures(zoom) else Modifier),
    ) {
        val paddingPx = 0f
        val viewportWidth = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
        zoom.viewportWidth = viewportWidth
        zoom.padding = paddingPx
        zoom.pageAspect = { i -> sizes.getOrNull(i)?.let { (it.height / it.width).toFloat() } ?: 1.414f }

        val basePageWidthPx = viewportWidth.toInt()
        val renderWidthPx = if (basePageWidthPx > 0) {
            ZoomMath.renderWidthPx(basePageWidthPx, if (zoomable) zoom.scale else 1f)
        } else {
            0
        }

        LaunchedEffect(manager, state, pageIndex, renderWidthPx, mode) {
            if (renderWidthPx <= 0) return@LaunchedEffect
            snapshotFlow {
                when (mode) {
                    ReadingMode.SinglePage -> {
                        pageIndex to pageIndex
                    }
                    else -> {
                        val info = state.layoutInfo
                        val visible = info.visibleItemsInfo.filter {
                            it.offset + it.size > info.viewportStartOffset && it.offset < info.viewportEndOffset
                        }
                        if (visible.isEmpty()) null else visible.first().index to visible.last().index
                    }
                }
            }
                .distinctUntilChanged()
                .collect { range -> range?.let { manager.setViewport(it.first, it.second, renderWidthPx) } }
        }

        when (mode) {
            ReadingMode.Vertical ->             VerticalPageList(
                manager = manager,
                pageCount = pageCount,
                state = state,
                zoom = zoom,
                viewportWidth = viewportWidth,
                renderWidthPx = renderWidthPx,
                viewport = viewport,
                layer = layer,
                registry = registry,
                pageOverlays = pageOverlays,
                selection = selection,
                autoScroll = autoScroll,
                onLinkTap = onLinkTap,
                topInset = topInset,
            )
            ReadingMode.SinglePage -> SinglePageView(
                manager = manager,
                pageIndex = pageIndex,
                pageCount = pageCount,
                renderWidthPx = renderWidthPx,
                viewport = viewport,
                layer = layer,
                registry = registry,
                pageOverlays = pageOverlays,
                selection = selection,
                zoom = zoom,
                autoScroll = autoScroll,
                onLinkTap = onLinkTap,
                onTurnPage = { delta ->
                    val target = (pageIndex + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    if (target != pageIndex) onPageIndexChange(target)
                },
            )
        }
    }
}

@Composable
private fun VerticalPageList(
    manager: PdfDocumentManager,
    pageCount: Int,
    state: LazyListState,
    zoom: ZoomState,
    viewportWidth: Float,
    renderWidthPx: Int,
    viewport: ViewportRef,
    layer: ViewportRef,
    registry: PageRegistry,
    pageOverlays: List<PageOverlay>,
    selection: SelectionState?,
    autoScroll: (Offset) -> Unit,
    onLinkTap: (Int, Float, Float) -> Unit,
    topInset: Dp,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.gestureScale
                scaleY = zoom.gestureScale
                translationX = zoom.gestureTranslation.x
                translationY = zoom.gestureTranslation.y
            }
            .onGloballyPositioned { layer.coordinates = it },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val width = ZoomMath.columnWidth(viewportWidth, zoom.scale).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(-zoom.offsetX.roundToInt(), 0)
                    }
                },
            state = state,
            contentPadding = PaddingValues(top = topInset),
            verticalArrangement = Arrangement.spacedBy(PageGap),
        ) {
            items(count = pageCount, key = { it }) { index ->
                PdfPageImage(
                    manager = manager,
                    index = index,
                    renderWidthPx = renderWidthPx,
                    modifier = Modifier.fillMaxWidth(),
                    highRes = HighResContext(zoom, viewport),
                    overlays = pageOverlays,
                    registry = registry,
                    gestures = if (selection != null) {
                        Modifier.selectionGestures(selection, registry, index, zoom, autoScroll) { nx, ny -> onLinkTap(index, nx, ny) }
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (selection != null) SelectionHandles(selection, registry, layer, zoom, autoScroll)
    }
}

@Composable
private fun SinglePageView(
    manager: PdfDocumentManager,
    pageIndex: Int,
    pageCount: Int,
    renderWidthPx: Int,
    viewport: ViewportRef,
    layer: ViewportRef,
    registry: PageRegistry,
    pageOverlays: List<PageOverlay>,
    selection: SelectionState?,
    zoom: ZoomState,
    autoScroll: (Offset) -> Unit,
    onLinkTap: (Int, Float, Float) -> Unit,
    onTurnPage: (Int) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { layer.coordinates = it }
            .pointerInput(pageIndex, pageCount) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total < -64f -> onTurnPage(1)
                            total > 64f -> onTurnPage(-1)
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                )
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PdfPageImage(
                manager = manager,
                index = pageIndex,
                renderWidthPx = renderWidthPx,
                modifier = Modifier.fillMaxWidth(),
                highRes = HighResContext(zoom, viewport),
                overlays = pageOverlays,
                registry = registry,
                gestures = if (selection != null) {
                    Modifier.selectionGestures(selection, registry, pageIndex, zoom, autoScroll) { nx, ny -> onLinkTap(pageIndex, nx, ny) }
                } else {
                    Modifier
                },
            )
        }
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onTurnPage(-1) },
            )
            Box(Modifier.weight(1f).fillMaxHeight())
            Box(
                Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onTurnPage(1) },
            )
        }
        if (selection != null) SelectionHandles(selection, registry, layer, zoom, autoScroll)
    }
}

// Other schemes (file:, intent:, javascript:) are ignored.
private val ExternalSchemes = setOf("http", "https", "mailto", "tel")

private fun openExternalLink(context: Context, raw: String) {
    val uri = (if ("://" in raw || raw.startsWith("mailto:") || raw.startsWith("tel:")) raw else "https://$raw").toUri()
    if (uri.scheme?.lowercase() !in ExternalSchemes) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.viewer_no_app_for_link), Toast.LENGTH_SHORT).show()
    }
}

private const val AUTO_SCROLL_EDGE = 0.08f

private const val AUTO_SCROLL_SPEED = 0.5f

// Scrolls only if the hit is not fully visible.
private suspend fun scrollToHit(hit: SearchHit, list: LazyListState, zoom: ZoomState) {
    if (hit.rects.isEmpty()) return
    val bounds = NormRect(
        hit.rects.minOf { it.left }, hit.rects.minOf { it.top },
        hit.rects.maxOf { it.right }, hit.rects.maxOf { it.bottom },
    )
    val info = list.layoutInfo
    val viewportHeight = info.viewportSize.height.toFloat()
    val pageWidth = ZoomMath.pageWidth(zoom.viewportWidth, zoom.scale, zoom.padding)
    val itemHeight = { i: Int -> pageWidth * zoom.pageAspect(i) }
    val margin = viewportHeight * 0.1f

    val item = info.visibleItemsInfo.firstOrNull { it.index == hit.pageIndex }
    val top = item?.let { it.offset - info.viewportStartOffset + bounds.top * itemHeight(hit.pageIndex) }
    val bottom = item?.let { it.offset - info.viewportStartOffset + bounds.bottom * itemHeight(hit.pageIndex) }
    if (top == null || bottom == null || top < margin || bottom > viewportHeight - margin) {
        val (index, offset) = ZoomMath.anchoredScroll(
            itemIndex = hit.pageIndex,
            offsetInItem = bounds.centerY * itemHeight(hit.pageIndex),
            focusY = viewportHeight / 2,
            topPadding = info.beforeContentPadding.toFloat(),
            spacing = info.mainAxisItemSpacing.toFloat(),
            itemHeight = itemHeight,
        )
        list.scrollToItem(index, offset)
    }

    val left = zoom.padding + bounds.left * pageWidth - zoom.offsetX
    val right = zoom.padding + bounds.right * pageWidth - zoom.offsetX
    if (left < 0 || right > zoom.viewportWidth) {
        zoom.scrollToOffsetX(zoom.padding + bounds.centerX * pageWidth - zoom.viewportWidth / 2)
    }
}

internal class HighResContext(val zoom: ZoomState, val viewport: ViewportRef)

private sealed interface PageImageState {
    data object Loading : PageImageState
    data class Ready(val image: ImageBitmap) : PageImageState
    data class Failed(val error: Throwable) : PageImageState
}

private class RegionPatch(val pageWidthPx: Int, val region: AndroidRect, val image: ImageBitmap)

// While a new size renders, the old image is still shown.
@Composable
internal fun PdfPageImage(
    manager: PdfDocumentManager,
    index: Int,
    renderWidthPx: Int,
    modifier: Modifier = Modifier,
    highRes: HighResContext? = null,
    overlays: List<PageOverlay> = emptyList(),
    registry: PageRegistry? = null,
    gestures: Modifier = Modifier,
) {
    val sizes by manager.pageSizes.collectAsState()
    val version by manager.version.collectAsState()
    val size = sizes.getOrNull(index) ?: return

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio((size.width / size.height).toFloat())
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val layoutWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val initial = (if (renderWidthPx > 0) manager.cachedPage(index, renderWidthPx) else null)
            ?.let { PageImageState.Ready(it) } ?: PageImageState.Loading

        val state by produceState(initial, manager, index, renderWidthPx, version) {
            if (renderWidthPx <= 0) return@produceState
            // The value survives key changes, so re-check the cache for the new keys.
            manager.cachedPage(index, renderWidthPx)?.let {
                value = PageImageState.Ready(it)
                return@produceState
            }
            value = try {
                PageImageState.Ready(manager.renderPage(index, renderWidthPx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PageImageState.Failed(e)
            }
        }

        // Sharp patch over the visible part when the full-page bitmap is capped.
        var patch by remember { mutableStateOf<RegionPatch?>(null) }
        val coordinates = remember { ViewportRef() } // This page's coordinates.
        var positionTick by remember { mutableIntStateOf(0) }
        if (highRes != null) {
            val capped = renderWidthPx > 0 && manager.isRenderCapped(index, renderWidthPx)
            LaunchedEffect(manager, index, layoutWidthPx, version, capped) {
                patch = patch?.takeIf { capped && it.pageWidthPx == layoutWidthPx }
                if (!capped || layoutWidthPx <= 0) return@LaunchedEffect
                snapshotFlow {
                    positionTick // Re-evaluated whenever the page moves.
                    if (highRes.zoom.isTransforming) null
                    else visibleRegion(coordinates.coordinates, highRes.viewport.coordinates)
                }
                    .distinctUntilChanged()
                    .collectLatest { region ->
                        if (region == null || patch?.region?.contains(region) == true) return@collectLatest
                        delay(HIGH_RES_DEBOUNCE_MS)
                        patch = try {
                            RegionPatch(layoutWidthPx, region, manager.renderRegion(index, layoutWidthPx, region))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null // The scaled full-page image stays visible.
                        }
                    }
            }
        }

        if (registry != null) {
            DisposableEffect(registry, index) {
                onDispose { registry.remove(index, coordinates.coordinates) }
            }
        }

        val pageModifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                coordinates.coordinates = it
                positionTick++
                registry?.update(index, it)
            }
            .then(gestures)
            .drawWithContent {
                drawContent()
                val current = patch
                if (current != null && current.pageWidthPx == layoutWidthPx) {
                    val r = current.region
                    drawImage(
                        image = current.image,
                        dstOffset = IntOffset(r.left, r.top),
                        dstSize = IntSize(r.width(), r.height()),
                    )
                }
                overlays.forEach { with(it) { drawOverlay(index) } }
            }

        when (val current = state) {
            is PageImageState.Ready -> Image(
                bitmap = current.image,
                contentDescription = stringResource(R.string.viewer_page_description, index + 1),
                modifier = pageModifier,
                contentScale = ContentScale.FillBounds,
            )
            PageImageState.Loading -> Box(pageModifier, contentAlignment = Alignment.Center) {
                Text("${index + 1}", color = Color.LightGray, style = MaterialTheme.typography.headlineMedium)
            }
            is PageImageState.Failed -> Text(
                text = stringResource(R.string.viewer_render_error, current.error.message.orEmpty()),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun visibleRegion(page: LayoutCoordinates?, viewport: LayoutCoordinates?): AndroidRect? {
    if (page == null || viewport == null || !page.isAttached || !viewport.isAttached) return null
    val bounds = page.localBoundingBoxOf(viewport, clipBounds = false)
        .intersect(Rect(0f, 0f, page.size.width.toFloat(), page.size.height.toFloat()))
    if (bounds.isEmpty) return null
    return AndroidRect(
        floor(bounds.left).toInt(), floor(bounds.top).toInt(),
        ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt(),
    )
}
