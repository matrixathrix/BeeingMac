package com.example.beeing

import kotlinx.serialization.Serializable
import java.util.Calendar

// ============================================================
// NOTIFICATION PREFS  (silent hours + pause — HOURLY REMINDERS ONLY)
// ============================================================
//
// Streak alerts and the weekly report are a different kind of notification: they
// always arrive, and the only setting that touches them is silent hours (they post
// silently then). Everything here is a pure function of the prefs and the clock,
// so the receiver, the settings rows and the tests cannot disagree.

@Serializable
enum class PauseMode { QUIET, OFF }

enum class Delivery { SOUND, SILENT, NONE }

@Serializable
data class NotificationPrefs(
    val silentOn: Boolean,
    val silentStartMin: Int,   // minutes after midnight
    val silentEndMin: Int,
    val pauseUntilMs: Long,    // absolute epoch ms; 0 = no pause
    val pauseMode: PauseMode
)

/** Silent hours may cross midnight (22:00 to 07:00); start == end means off. */
fun inSilentHours(p: NotificationPrefs, nowMs: Long): Boolean {
    if (!p.silentOn || p.silentStartMin == p.silentEndMin) return false
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    return if (p.silentStartMin < p.silentEndMin) m >= p.silentStartMin && m < p.silentEndMin
    else m >= p.silentStartMin || m < p.silentEndMin
}

/** A pause is an absolute end instant, so it survives reboots and clock changes. */
fun pauseActive(p: NotificationPrefs, nowMs: Long): Boolean = p.pauseUntilMs > nowMs

fun hourlyDelivery(p: NotificationPrefs, nowMs: Long): Delivery = when {
    pauseActive(p, nowMs) && p.pauseMode == PauseMode.OFF -> Delivery.NONE
    pauseActive(p, nowMs) -> Delivery.SILENT
    inSilentHours(p, nowMs) -> Delivery.SILENT
    else -> Delivery.SOUND
}

/** Streak alerts and the weekly report: silent in silent hours, never suppressed. */
fun otherDelivery(p: NotificationPrefs, nowMs: Long): Delivery =
    if (inSilentHours(p, nowMs)) Delivery.SILENT else Delivery.SOUND

/**
 * The phone's hourly reminder when Beeing is on a watch: while the watch is reachable
 * and off its charger it does the buzzing, so the phone's copy arrives silently. On
 * the charger it isn't being worn, so the phone rings.
 */
fun phoneDelivery(base: Delivery, watchReachable: Boolean, watchCharging: Boolean): Delivery =
    if (base == Delivery.SOUND && watchReachable && !watchCharging) Delivery.SILENT else base

/** The longest a pause may run: 48 hours from now, floored to the local hour. */
fun maxPauseUntil(nowMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = nowMs
    add(Calendar.HOUR_OF_DAY, 48)
    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * What a notifications-OFF pause could cost. `days` are the Master days it overlaps
 * (today excluded once it has qualified); `breakDayStartMs` is the first day the
 * saver balance would run out; `fadeCount` is the no-streak version ("you could lose k").
 */
data class PauseRisk(
    val days: Int,
    val savers: Int,
    val streak: Int,
    val breakDayStartMs: Long?,
    val fadeCount: Int
)

fun pauseRisk(state: StreakState, todayQualified: Boolean, beginnerMode: Boolean, nowMs: Long, untilMs: Long): PauseRisk? {
    if (beginnerMode) return null
    val cursor = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val dayStarts = ArrayList<Long>()
    var isToday = true
    while (cursor.timeInMillis < untilMs) {
        if (!(isToday && todayQualified)) dayStarts.add(cursor.timeInMillis)
        isToday = false
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }
    val days = dayStarts.size
    return when {
        days == 0 -> null
        state.currentStreak > 0 ->
            if (days > state.savers) PauseRisk(days, state.savers, state.currentStreak, dayStarts[state.savers], 0)
            else null
        state.savers > 0 -> PauseRisk(days, state.savers, 0, null, minOf(days, state.savers))
        else -> null
    }
}
