package com.example.pdf_reader.ui.pdf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

// Called once: true = single tap, false = first half of a double tap.
internal fun interface TapResolver {
    fun resolve(confirmed: Boolean)
}

internal object TapClassifier {
    fun isTap(downMs: Long, upMs: Long, longPressMs: Long): Boolean = upMs - downMs < longPressMs

    fun withinSlop(dx: Float, dy: Float, slop: Float): Boolean = hypot(dx, dy) <= slop

    // Second down already came within the double tap timeout.
    fun isDoubleTap(dx: Float, dy: Float, slop: Float): Boolean = hypot(dx, dy) < slop * DOUBLE_TAP_SLOP

    private const val DOUBLE_TAP_SLOP = 4f
}

// The only tap detector of the viewer. Positions are local to the modified node.
internal fun Modifier.viewerTapGestures(
    doubleTapEnabled: Boolean,
    onTap: (Offset) -> TapResolver,
    onDoubleTap: (Offset) -> Unit,
): Modifier = pointerInput(doubleTapEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val up = awaitTapUp(down) ?: return@awaitEachGesture
        val resolver = onTap(up.position)
        if (!doubleTapEnabled) {
            resolver.resolve(true)
            return@awaitEachGesture
        }
        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false)
        }
        val delta = second?.let { it.position - down.position }
        val isDouble = delta != null && TapClassifier.isDoubleTap(delta.x, delta.y, viewConfiguration.touchSlop)
        resolver.resolve(!isDouble)
        if (second == null) return@awaitEachGesture
        val secondUp = awaitTapUp(second) ?: return@awaitEachGesture
        if (isDouble) onDoubleTap(second.position) else onTap(secondUp.position).resolve(true)
    }
}

// Null when it turned into a scroll, drag, pinch or long press.
private suspend fun AwaitPointerEventScope.awaitTapUp(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if (event.changes.any { it.isConsumed }) return null
        if (event.changes.any { it.id != down.id && it.pressed }) return null
        val move = change.position - down.position
        if (!TapClassifier.withinSlop(move.x, move.y, viewConfiguration.touchSlop)) return null
        if (change.changedToUp()) {
            val quick = TapClassifier.isTap(down.uptimeMillis, change.uptimeMillis, viewConfiguration.longPressTimeoutMillis)
            return change.takeIf { quick }
        }
    }
}
