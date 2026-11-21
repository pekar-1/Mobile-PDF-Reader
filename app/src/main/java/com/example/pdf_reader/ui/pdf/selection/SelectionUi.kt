package com.example.pdf_reader.ui.pdf.selection

import android.content.ClipData
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.pdfium.NormRect
import com.example.pdf_reader.pdfium.TextPosition
import com.example.pdf_reader.ui.pdf.PageRegistry
import com.example.pdf_reader.ui.pdf.ViewportRef
import com.example.pdf_reader.ui.pdf.zoom.ZoomState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HandleSize = 24.dp

// Long press = select word, drag = extend. Taps are in viewerTapGestures.
@Composable
internal fun Modifier.selectionGestures(
    selection: SelectionState,
    registry: PageRegistry,
    pageIndex: Int,
    zoom: ZoomState,
    autoScroll: (window: Offset) -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    return this
        .pointerInput(selection, pageIndex) {
            fun finish() {
                zoom.panEnabled = true
                selection.endDrag()
            }
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    zoom.panEnabled = false
                    selection.isDragging = true
                    selection.selectWordAt(pageIndex, local.x / size.width, local.y / size.height)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val coords = registry.coordinates(pageIndex) ?: return@detectDragGesturesAfterLongPress
                    val window = coords.localToWindow(change.position)
                    registry.hitTest(window)?.let { selection.extendTo(it.pageIndex, it.nx, it.ny) }
                    autoScroll(window)
                },
                onDragEnd = ::finish,
                onDragCancel = ::finish,
            )
        }
}

// Drawn above all pages, so a handle can be dragged to another page.
@Composable
internal fun SelectionHandles(
    selection: SelectionState,
    registry: PageRegistry,
    layer: ViewportRef,
    zoom: ZoomState,
    autoScroll: (window: Offset) -> Unit,
) {
    if (!selection.hasSelection) return
    SelectionHandle(isStart = true, selection, registry, layer, zoom, autoScroll)
    SelectionHandle(isStart = false, selection, registry, layer, zoom, autoScroll)
}

@Composable
private fun SelectionHandle(
    isStart: Boolean,
    selection: SelectionState,
    registry: PageRegistry,
    layer: ViewportRef,
    zoom: ZoomState,
    autoScroll: (window: Offset) -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    val self = remember { ViewportRef() }

    Box(
        Modifier
            .offset {
                registry.tick // Follow scrolling and zoom.
                val tip = handleTip(isStart, selection, registry, layer)
                    ?: return@offset IntOffset(-100_000, -100_000)
                val sizePx = HandleSize.toPx()
                IntOffset((if (isStart) tip.x - sizePx else tip.x).roundToInt(), tip.y.roundToInt())
            }
            .size(HandleSize)
            .onGloballyPositioned { self.coordinates = it }
            .pointerInput(isStart, selection) {
                var fingerStart = Offset.Zero
                var tipStart = Offset.Zero
                var lift = 0f
                fun finish() {
                    zoom.panEnabled = true
                    selection.endDrag()
                }
                detectDragGestures(
                    onDragStart = { local ->
                        val me = self.coordinates ?: return@detectDragGestures
                        fingerStart = me.localToWindow(local)
                        // The tip is the corner touching the text line.
                        tipStart = me.localToWindow(Offset(if (isStart) size.width.toFloat() else 0f, 0f))
                        lift = lineHeightPx(isStart, selection, registry) / 2
                        selection.beginHandleDrag(isStart)
                        zoom.panEnabled = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val me = self.coordinates ?: return@detectDragGestures
                        val finger = me.localToWindow(change.position)
                        // Aim at the middle of the line above the tip, not under the finger.
                        val target = tipStart + (finger - fingerStart) - Offset(0f, lift)
                        registry.hitTest(target)?.let { selection.extendTo(it.pageIndex, it.nx, it.ny) }
                        autoScroll(finger)
                    },
                    onDragEnd = ::finish,
                    onDragCancel = ::finish,
                )
            }
            .drawBehind {
                // Teardrop: a circle with the corner at the tip squared off.
                drawCircle(color, radius = size.width / 2, center = center)
                val corner = Size(size.width / 2, size.height / 2)
                drawRect(color, topLeft = if (isStart) Offset(size.width / 2, 0f) else Offset.Zero, size = corner)
            },
    )
}

private fun handleTip(isStart: Boolean, selection: SelectionState, registry: PageRegistry, layer: ViewportRef): Offset? {
    val position = (if (isStart) selection.start else selection.end) ?: return null
    val box = (if (isStart) selection.startBox else selection.endBox) ?: return null
    val page = registry.coordinates(position.pageIndex) ?: return null
    val layerCoords = layer.coordinates?.takeIf { it.isAttached } ?: return null
    val local = Offset(
        (if (isStart) box.left else box.right) * page.size.width,
        box.bottom * page.size.height,
    )
    return layerCoords.localPositionOf(page, local)
}

private fun lineHeightPx(isStart: Boolean, selection: SelectionState, registry: PageRegistry): Float {
    val position = (if (isStart) selection.start else selection.end) ?: return 0f
    val box = (if (isStart) selection.startBox else selection.endBox) ?: return 0f
    val page = registry.coordinates(position.pageIndex) ?: return 0f
    val top = page.localToWindow(Offset(0f, box.top * page.size.height))
    val bottom = page.localToWindow(Offset(0f, box.bottom * page.size.height))
    return bottom.y - top.y
}

// System Copy / Select all menu, hidden while something moves.
@Composable
internal fun SelectionToolbar(
    selection: SelectionState,
    registry: PageRegistry,
    list: LazyListState,
    zoom: ZoomState,
) {
    val toolbar = LocalTextToolbar.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val idle = selection.hasSelection && !selection.isDragging && !list.isScrollInProgress &&
        !zoom.isTransforming && selection.startBox != null

    LaunchedEffect(idle, selection.start, selection.end, selection.startBox) {
        val rect = if (idle) selectionBoundsInRoot(selection, registry) else null
        if (rect == null) {
            toolbar.hide()
            return@LaunchedEffect
        }
        toolbar.showMenu(
            rect = rect,
            onCopyRequested = {
                scope.launch {
                    val text = selection.text()
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("PDF", text)))
                    selection.clear()
                }
            },
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = { selection.selectAllOnPage() },
        )
    }
    DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
}

private fun selectionBoundsInRoot(selection: SelectionState, registry: PageRegistry): Rect? {
    fun rootRect(position: TextPosition?, box: NormRect?): Rect? {
        if (position == null || box == null) return null
        val page: LayoutCoordinates = registry.coordinates(position.pageIndex) ?: return null
        val w = page.size.width
        val h = page.size.height
        val topLeft = page.localToRoot(Offset(box.left * w, box.top * h))
        val bottomRight = page.localToRoot(Offset(box.right * w, box.bottom * h))
        return Rect(topLeft, bottomRight)
    }
    val a = rootRect(selection.start, selection.startBox)
    val b = rootRect(selection.end, selection.endBox)
    return when {
        a != null && b != null -> Rect(minOf(a.left, b.left), a.top, maxOf(a.right, b.right), b.bottom)
        else -> a ?: b
    }
}
