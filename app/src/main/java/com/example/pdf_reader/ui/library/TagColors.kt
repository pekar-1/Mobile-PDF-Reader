package com.example.pdf_reader.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object TagColors {
    val palette = listOf(
        Color(0xFF2F6FED),
        Color(0xFF1A8A9A),
        Color(0xFF3D6B4F),
        Color(0xFF5B4FC9),
        Color(0xFFC44B4B),
        Color(0xFFC47A1A),
    )

    fun toHsv(color: Color): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv
    }

    fun fromHsv(hue: Float, sat: Float, value: Float): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
}
