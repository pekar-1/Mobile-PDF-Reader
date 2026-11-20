package com.example.pdf_reader.ui.pdf.zoom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// While pinching we only draw a scaled picture (gestureScale/gestureTranslation),
// no relayout. After the gesture, new scale + offsetX + scroll are set at once.
@Stable
class ZoomState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var gestureScale by mutableFloatStateOf(1f)
        private set
    var gestureTranslation by mutableStateOf(Offset.Zero)
        private set

    val isTransforming: Boolean get() = gestureScale != 1f || gestureTranslation != Offset.Zero

    internal var viewportWidth = 0f
    internal var padding = 0f

    // height / width of each page
    internal var pageAspect: (Int) -> Float = { 1.414f }

    // e.g. while dragging a selection handle
    var panEnabled = true

    private var flingJob: Job? = null
    private var animationJob: Job? = null

    internal fun updateGesture(rawScale: Float, centroid: Offset, startCentroid: Offset) {
        val g = ZoomMath.clampScale(scale * rawScale) / scale
        gestureScale = g
        // Point under startCentroid follows the fingers: p' = p * g + t.
        gestureTranslation = centroid - startCentroid * g
    }

    internal fun panBy(dx: Float) {
        if (!panEnabled) return
        offsetX = ZoomMath.clampOffsetX(offsetX + dx, viewportWidth, scale)
    }

    fun scrollToOffsetX(x: Float) {
        stopAnimations()
        offsetX = ZoomMath.clampOffsetX(x, viewportWidth, scale)
    }

    internal fun fling(velocityX: Float) {
        if (!panEnabled || scale <= ZoomMath.MIN_SCALE) return
        flingJob = scope.launch {
            val anim = Animatable(offsetX)
            anim.updateBounds(0f, ZoomMath.maxOffsetX(viewportWidth, scale)) // Stops at the edges.
            anim.animateDecay(-velocityX, exponentialDecay()) { offsetX = value }
        }
    }

    internal fun stopAnimations() {
        flingJob?.cancel()
    }

    internal fun commitGesture(startCentroid: Offset) {
        val from = scale
        val to = ZoomMath.clampScale(from * gestureScale)
        // Where the content under startCentroid is drawn now.
        val focusAfter = startCentroid * gestureScale + gestureTranslation
        commit(from, to, startCentroid, focusAfter)
    }

    internal fun toggleZoom(focus: Offset) {
        if (animationJob?.isActive == true) return
        val target = if (scale > ZoomMath.MIN_SCALE + 0.01f) ZoomMath.MIN_SCALE else ZoomMath.DOUBLE_TAP_SCALE
        animationJob = scope.launch {
            val anim = Animatable(1f)
            anim.animateTo(target / scale, tween(DOUBLE_TAP_MILLIS)) {
                updateGesture(value, focus, focus)
            }
            commitGesture(focus)
        }
    }

    private fun commit(from: Float, to: Float, focusBefore: Offset, focusAfter: Offset) {
        val info = listState.layoutInfo
        val ratio = ZoomMath.pageWidth(viewportWidth, to, padding) / ZoomMath.pageWidth(viewportWidth, from, padding)
        // Item under focusBefore in the current (committed) layout.
        val anchor = info.visibleItemsInfo.lastOrNull { it.offset - info.viewportStartOffset <= focusBefore.y }
            ?: info.visibleItemsInfo.firstOrNull()
        Snapshot.withMutableSnapshot {
            if (anchor != null) {
                val offsetInItem = focusBefore.y - (anchor.offset - info.viewportStartOffset)
                val newPageWidth = ZoomMath.pageWidth(viewportWidth, to, padding)
                val (index, scroll) = ZoomMath.anchoredScroll(
                    itemIndex = anchor.index,
                    offsetInItem = offsetInItem * ratio,
                    focusY = focusAfter.y,
                    topPadding = info.beforeContentPadding.toFloat(),
                    spacing = info.mainAxisItemSpacing.toFloat(),
                    itemHeight = { newPageWidth * pageAspect(it) },
                )
                listState.requestScrollToItem(index, scroll)
            }
            offsetX = ZoomMath.anchoredOffsetX(offsetX, from, to, focusBefore.x, focusAfter.x, viewportWidth, padding)
            scale = to
            gestureScale = 1f
            gestureTranslation = Offset.Zero
        }
    }

    private companion object {
        const val DOUBLE_TAP_MILLIS = 220
    }
}

@Composable
fun rememberZoomState(listState: LazyListState, scope: CoroutineScope): ZoomState =
    remember(listState, scope) { ZoomState(listState, scope) }

// Initial pass, so pinch is handled before the list scrolls.
// One finger: list scrolls vertically, we pan horizontally.
fun Modifier.zoomGestures(state: ZoomState): Modifier = pointerInput(state) {
    var lastTapUptime = 0L
    var lastTapPosition = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.stopAnimations()
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var pinching = false
        var panning = false
        var moved = 0f
        var rawScale = 1f
        var startCentroid = Offset.Zero
        var upTime = down.uptimeMillis

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                upTime = event.changes.maxOf { it.uptimeMillis }
                break
            }
            if (pressed.size >= 2 && !pinching) {
                pinching = true
                rawScale = 1f
                startCentroid = event.calculateCentroid(useCurrent = false)
            }
            if (pinching) {
                if (pressed.size >= 2) {
                    rawScale *= event.calculateZoom()
                    state.updateGesture(rawScale, event.calculateCentroid(useCurrent = true), startCentroid)
                }
                event.changes.forEach { it.consume() }
            } else {
                val change = pressed.first()
                tracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.position - change.previousPosition
                moved += delta.getDistance()
                if (!panning && moved > slop) panning = true
                if (panning && state.scale > ZoomMath.MIN_SCALE) state.panBy(-delta.x)
            }
        }

        when {
            pinching -> state.commitGesture(startCentroid)
            panning -> state.fling(tracker.calculateVelocity().x)
            upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis -> {
                val isDoubleTap = down.uptimeMillis - lastTapUptime < viewConfiguration.doubleTapTimeoutMillis &&
                    (down.position - lastTapPosition).getDistance() < slop * 4
                if (isDoubleTap) {
                    lastTapUptime = 0L
                    state.toggleZoom(down.position)
                } else {
                    lastTapUptime = upTime
                    lastTapPosition = down.position
                }
            }
        }
    }
}
