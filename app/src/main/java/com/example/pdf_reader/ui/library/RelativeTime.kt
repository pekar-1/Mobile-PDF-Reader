package com.example.pdf_reader.ui.library

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun relativeOpenedAt(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val delta = (nowMs - epochMs).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "przed chwilą"
        minutes < 60 -> "$minutes min temu"
        hours < 24 -> "$hours godz. temu"
        days == 1L -> "wczoraj"
        days < 7 -> "$days dni temu"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, Locale("pl", "PL")).format(Date(epochMs))
    }
}

fun pageCountLabel(count: Int): String {
    if (count <= 0) return "—"
    val abs = count % 100
    val last = count % 10
    val word = when {
        last == 1 && abs != 11 -> "strona"
        last in 2..4 && abs !in 12..14 -> "strony"
        else -> "stron"
    }
    return "$count $word"
}
