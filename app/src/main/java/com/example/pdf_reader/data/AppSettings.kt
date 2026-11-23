package com.example.pdf_reader.data

import android.content.Context

object AppSettings {
    const val PREFS = "settings"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    val themes = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)

    const val KEY_THEME = "theme"

    fun themeMode(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_SYSTEM)
        return if (stored in themes) stored!! else THEME_SYSTEM
    }

    fun setThemeMode(context: Context, mode: String) {
        val value = if (mode in themes) mode else THEME_SYSTEM
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, value)
            .apply()
    }
}
