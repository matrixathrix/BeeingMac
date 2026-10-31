package com.example.beeing

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Pure helpers for a selected Trends bar (the numbers below scope to it).

/**
 * What a selected bar's ▲ ▼ tag changes compare against: a month bar against the
 * calendar month before it; a week bar, which is clipped to 1–7 days at a month's
 * edge, against the same number of days just before it. Calendar arithmetic, so a
 * week across a clock change still starts at midnight.
 */
internal fun previousSpan(startMs: Long, endMs: Long, monthly: Boolean): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply { timeInMillis = startMs }
    if (monthly) {
        cal.add(Calendar.MONTH, -1)
    } else {
        var days = 0
        val walk = Calendar.getInstance().apply { timeInMillis = startMs }
        while (walk.timeInMillis < endMs) { walk.add(Calendar.DAY_OF_YEAR, 1); days++ }
        cal.add(Calendar.DAY_OF_YEAR, -days)
    }
    return cal.timeInMillis to startMs
}

/**
 * The scope chip's text: "12–18 Oct" for a week bar ("31 Oct" for a one-day
 * sliver, "28 Sep – 4 Oct" if it ever spans two months), "March 2026" for a month.
 */
internal fun spanLabel(startMs: Long, endMs: Long, monthly: Boolean, locale: Locale = Locale.getDefault()): String {
    if (monthly) return SimpleDateFormat("MMMM yyyy", locale).format(Date(startMs))
    val a = Calendar.getInstance().apply { timeInMillis = startMs }
    val b = Calendar.getInstance().apply { timeInMillis = endMs - 1 }
    val month = SimpleDateFormat("MMM", locale)
    val dayA = a.get(Calendar.DAY_OF_MONTH)
    val dayB = b.get(Calendar.DAY_OF_MONTH)
    val sameMonth = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
    return when {
        sameMonth && dayA == dayB -> "$dayA ${month.format(a.time)}"
        sameMonth -> "$dayA–$dayB ${month.format(a.time)}"
        else -> "$dayA ${month.format(a.time)} – $dayB ${month.format(b.time)}"
    }
}
