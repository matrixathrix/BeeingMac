package com.example.beeing

import java.util.Calendar

/*
 * Super day, the pure half: how many more hours a day can still collect, and the
 * invite / cue / late-hint decisions that hang off that number. No Context, no Compose.
 * A rated hour's timestamp is the START of the hour, and it belongs to the day its start
 * falls in.
 */

/** Epoch ms of the clock hour [hours] hours from [startMs] (negative = earlier), DST-aware. */
private fun hourStartOffset(startMs: Long, hours: Int): Long = Calendar.getInstance().apply {
    timeInMillis = startMs
    add(Calendar.HOUR_OF_DAY, hours)
}.timeInMillis

/** Epoch ms of the start of the day after the one starting at [dayStartMs], DST-aware. */
private fun nextDayStart(dayStartMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dayStartMs
    add(Calendar.DAY_OF_YEAR, 1)
}.timeInMillis

/**
 * How many more hours the day starting at [dayStartMs] can still collect, given the hours
 * already counted. Walks the day's real clock hours, so a DST day has 23 or 25.
 *
 * Each uncounted hour is either ratable right now (the two just-finished hours, always
 * reachable), still to come (reachable unless its end falls in silent hours or an OFF
 * pause, i.e. unless no reminder would reach the user then), or past and gone. The
 * reminders switch is ignored on purpose: it is the user's choice, not a limit on the day.
 */
fun reachableHours(
    dayStartMs: Long,
    countedStarts: Collection<Long>,
    prefs: NotificationPrefs,
    nowMs: Long
): Int {
    val cur = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val ratable = setOf(hourStartOffset(cur, -1), hourStartOffset(cur, -2))
    val dayEnd = nextDayStart(dayStartMs)

    var n = 0
    var start = dayStartMs
    while (start < dayEnd) {
        val end = hourStartOffset(start, 1)
        if (start !in countedStarts) {
            val reachable = when {
                start in ratable -> true
                start >= cur -> !inSilentHours(prefs, end) &&
                    !(prefs.pauseMode == PauseMode.OFF && prefs.pauseUntilMs > end)
                else -> false
            }
            if (reachable) n++
        }
        start = end
    }
    return n
}

/** "Make it a Super day": [hoursNeeded] more counted hours; [savers] decides the cap copy. */
data class SuperInvite(val hoursNeeded: Int, val savers: Int) {
    val atCap: Boolean get() = savers >= SAVER_CAP
}

/**
 * The invite shows only when the day can get there with a spare hour to give
 * ([SUPER_DAY_SLACK]), so it never promises something one missed reminder would break.
 */
fun superInviteFor(day: DayTally, savers: Int, reachable: Int): SuperInvite? {
    if (day.beginner || day.countedHours >= SUPER_DAY_HOURS) return null
    val needed = SUPER_DAY_HOURS - day.countedHours
    return if (reachable >= needed + SUPER_DAY_SLACK) SuperInvite(needed, savers) else null
}

/**
 * Hours left to a Super day, for the quiet caption once the goal is met: a Master day at
 * or past its goal, under 12, where the hours still fit exactly (no slack, it is only a caption).
 */
fun superCueFor(day: DayTally?, reachable: Int): Int? {
    if (day == null || day.beginner) return null
    val counted = day.countedHours
    if (counted < day.goal || counted >= SUPER_DAY_HOURS) return null
    val needed = SUPER_DAY_HOURS - counted
    return if (reachable >= needed) needed else null
}

/**
 * The Save-button hint when the hour being added late, to a finished Master day, would be
 * the one that tips it over. Super day outranks the ring.
 */
fun lateHourHint(hourStartMs: Long, days: List<DayTally>): String? {
    val day = days.firstOrNull { hourStartMs >= it.dayStartMs && hourStartMs < nextDayStart(it.dayStartMs) }
        ?: return null
    if (day.isToday || day.beginner || hourStartMs in day.countedStarts) return null
    return when (day.countedHours) {
        SUPER_DAY_HOURS - 1 -> " · finishes your Super day"
        day.goal - 1 -> " · closes yesterday's ring"
        else -> null
    }
}
