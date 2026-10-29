package com.example.beeing

import android.content.Context
import java.util.Calendar

// ============================================================
// NOTIFICATION PREFS  (silent hours + pause — HOURLY REMINDERS ONLY)
// ============================================================
//
// Streak alerts and the weekly report are a different kind of notification: they
// always arrive, and the only setting that touches them is silent hours (they post
// silently then). Everything here is a pure function of the prefs and the clock,
// so the receiver, the settings rows and the tests cannot disagree.

enum class PauseMode { QUIET, OFF }

enum class Delivery { SOUND, SILENT, NONE }

data class NotificationPrefs(
    val remindersOn: Boolean,
    val silentOn: Boolean,
    val silentStartMin: Int,   // minutes after midnight
    val silentEndMin: Int,
    val pauseUntilMs: Long,    // absolute epoch ms; 0 = no pause
    val pauseMode: PauseMode
)

private const val REMINDERS_KEY = "hourly_reminders"
private const val SILENT_ON_KEY = "silent_on"
private const val SILENT_START_KEY = "silent_start_min"
private const val SILENT_END_KEY = "silent_end_min"
private const val PAUSE_UNTIL_KEY = "pause_until_ms"
private const val PAUSE_MODE_KEY = "pause_mode"

fun loadNotificationPrefs(ctx: Context): NotificationPrefs {
    val p = ctx.getSharedPreferences("b", 0)
    return NotificationPrefs(
        remindersOn = p.getBoolean(REMINDERS_KEY, true),
        silentOn = p.getBoolean(SILENT_ON_KEY, false),
        silentStartMin = p.getInt(SILENT_START_KEY, 1320),
        silentEndMin = p.getInt(SILENT_END_KEY, 420),
        pauseUntilMs = p.getLong(PAUSE_UNTIL_KEY, 0L),
        pauseMode = if (p.getString(PAUSE_MODE_KEY, "OFF") == "QUIET") PauseMode.QUIET else PauseMode.OFF
    )
}

fun saveNotificationPrefs(ctx: Context, p: NotificationPrefs) {
    ctx.getSharedPreferences("b", 0).edit()
        .putBoolean(REMINDERS_KEY, p.remindersOn)
        .putBoolean(SILENT_ON_KEY, p.silentOn)
        .putInt(SILENT_START_KEY, p.silentStartMin)
        .putInt(SILENT_END_KEY, p.silentEndMin)
        .putLong(PAUSE_UNTIL_KEY, p.pauseUntilMs)
        .putString(PAUSE_MODE_KEY, p.pauseMode.name)
        .apply()
}

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
    !p.remindersOn -> Delivery.NONE
    pauseActive(p, nowMs) && p.pauseMode == PauseMode.OFF -> Delivery.NONE
    pauseActive(p, nowMs) -> Delivery.SILENT
    inSilentHours(p, nowMs) -> Delivery.SILENT
    else -> Delivery.SOUND
}

/** Streak alerts and the weekly report: silent in silent hours, never suppressed. */
fun otherDelivery(p: NotificationPrefs, nowMs: Long): Delivery =
    if (inSilentHours(p, nowMs)) Delivery.SILENT else Delivery.SOUND

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
