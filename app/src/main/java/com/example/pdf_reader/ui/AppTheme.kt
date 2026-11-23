package com.example.pdf_reader.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.pdf_reader.data.AppSettings

@Composable
fun rememberThemeMode(): Pair<String, (String) -> Unit> {
    val context = LocalContext.current.applicationContext
    var mode by remember { mutableStateOf(AppSettings.themeMode(context)) }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == AppSettings.KEY_THEME) mode = AppSettings.themeMode(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return mode to { value ->
        mode = value
        AppSettings.setThemeMode(context, value)
    }
}

@Composable
fun isDarkTheme(mode: String): Boolean = when (mode) {
    AppSettings.THEME_DARK -> true
    AppSettings.THEME_LIGHT -> false
    else -> isSystemInDarkTheme()
}
