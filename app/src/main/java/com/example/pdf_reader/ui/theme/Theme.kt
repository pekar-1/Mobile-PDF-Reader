package com.example.pdf_reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color(0xFF0B1220),
    secondary = SlateBlue,
    tertiary = AccentBlueDark,
    background = PaperDark,
    surface = CardDark,
    surfaceContainerHigh = Color(0xFF262D38),
    outlineVariant = Color(0xFF3A4452),
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = SlateBlue,
    tertiary = AccentBlue,
    background = PaperLight,
    surface = CardLight,
    surfaceContainerHigh = Color(0xFFEEF2F8),
    outlineVariant = Color(0xFFD5DCE6),
)

@Composable
fun Pdf_ReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.copy(
        background = if (darkTheme) PaperDark else PaperLight,
        surface = if (darkTheme) CardDark else CardLight,
        primary = if (darkTheme) AccentBlueDark else AccentBlue,
        onPrimary = if (darkTheme) Color(0xFF0B1220) else Color.White,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
