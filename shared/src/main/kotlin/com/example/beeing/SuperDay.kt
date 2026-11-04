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
 * Each uncounted hour is either ratable right now (the two just-finished hours), still to
 * come, or past and gone. Only the clock limits the day: silent hours, a pause and the
 * reminders switch are ignored on purpose, so the invite never comes and goes for
 * reasons the user can't see (owner call, 2026-10-31).
 */
fun reachableHours(dayStartMs: Long, countedStarts: Collection<Long>, nowMs: Long): Int {
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
            if (start in ratable || start >= cur) n++
        }
        start = end
    }
    return n
}

/**
 * "Make it a Super day": [hoursNeeded] more counted hours; [savers] decides the cap copy.
 * [dayStartMs] is the day it invites to, which the once-a-day flag records when it shows.
 * [tooLate]: the day has fewer hours left than it needs, so the screen says so instead.
 */
data class SuperInvite(
    val hoursNeeded: Int,
    val savers: Int,
    val dayStartMs: Long,
    val tooLate: Boolean = false
) {
    val atCap: Boolean get() = savers >= SAVER_CAP
}

/**
 * Every Master ring close today gets the screen: the invite when the clock still holds
 * the hours it needs, otherwise the "not enough hours" variant ([SuperInvite.tooLate]).
 * Yesterday's ring closed after midnight gets neither (its day is over).
 */
fun superInviteFor(day: DayTally, savers: Int, reachable: Int): SuperInvite? {
    if (day.beginner || !day.isToday || day.countedHours >= SUPER_DAY_HOURS) return null
    val needed = SUPER_DAY_HOURS - day.countedHours
    return SuperInvite(needed, savers, day.dayStartMs, tooLate = reachable < needed)
}

/** The invite shows at most once a day: [shownDay] is the day start it last showed on. */
fun gateInvite(invite: SuperInvite?, dayStartMs: Long, shownDay: Long): SuperInvite? =
    invite?.takeIf { shownDay != dayStartMs }

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
