package com.example.pdf_reader.ui.library

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.example.pdf_reader.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun relativeOpenedAt(res: Resources, epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val delta = (nowMs - epochMs).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> res.getString(R.string.time_just_now)
        minutes < 60 -> res.getQuantityString(R.plurals.time_minutes_ago, minutes.toInt(), minutes.toInt())
        hours < 24 -> res.getQuantityString(R.plurals.time_hours_ago, hours.toInt(), hours.toInt())
        days == 1L -> res.getString(R.string.time_yesterday)
        days < 7 -> res.getQuantityString(R.plurals.time_days_ago, days.toInt(), days.toInt())
        else -> {
            val locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.getDefault()
            DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMs))
        }
    }
}

fun pageCountLabel(res: Resources, count: Int): String {
    if (count <= 0) return "—"
    return res.getQuantityString(R.plurals.page_count, count, count)
}
