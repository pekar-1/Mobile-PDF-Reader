package com.example.pdf_reader.ui.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapClassifierTest {
    private val slop = 10f

    @Test
    fun quickPressIsTap() {
        assertTrue(TapClassifier.isTap(downMs = 0, upMs = 100, longPressMs = 400))
        assertFalse(TapClassifier.isTap(downMs = 0, upMs = 400, longPressMs = 400))
    }

    @Test
    fun movementBeyondSlopIsNotTap() {
        assertTrue(TapClassifier.withinSlop(6f, 8f, slop))
        assertFalse(TapClassifier.withinSlop(8f, 8f, slop))
    }

    @Test
    fun nearSecondTapIsDouble() {
        assertTrue(TapClassifier.isDoubleTap(20f, 20f, slop))
        assertFalse(TapClassifier.isDoubleTap(40f, 0f, slop))
        assertFalse(TapClassifier.isDoubleTap(0f, 300f, slop))
    }
}
