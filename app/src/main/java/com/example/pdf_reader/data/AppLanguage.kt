package com.example.pdf_reader.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app UI language. Every activity wraps its base context with [wrap];
 * [set] saves the choice and recreates the calling activity.
 */
// shortcut: SharedPreferences instead of DataStore, move when more settings arrive.
object AppLanguage {
    /** Empty tag = follow the system language. */
    const val SYSTEM = ""
    val supported = listOf("pl", "en")

    private const val PREFS = "settings"
    private const val KEY = "language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(activity: Activity, tag: String) {
        if (tag == get(activity)) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        activity.recreate()
    }

    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
