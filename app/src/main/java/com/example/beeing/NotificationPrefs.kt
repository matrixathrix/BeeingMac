package com.example.beeing

import android.content.Context

// Persistence for the pure rules in :shared NotificationRules.kt.

private const val SILENT_ON_KEY = "silent_on"
private const val SILENT_START_KEY = "silent_start_min"
private const val SILENT_END_KEY = "silent_end_min"
private const val PAUSE_UNTIL_KEY = "pause_until_ms"
private const val PAUSE_MODE_KEY = "pause_mode"

fun loadNotificationPrefs(ctx: Context): NotificationPrefs {
    val p = ctx.getSharedPreferences("b", 0)
    return NotificationPrefs(
        silentOn = p.getBoolean(SILENT_ON_KEY, false),
        silentStartMin = p.getInt(SILENT_START_KEY, 1320),
        silentEndMin = p.getInt(SILENT_END_KEY, 420),
        pauseUntilMs = p.getLong(PAUSE_UNTIL_KEY, 0L),
        pauseMode = if (p.getString(PAUSE_MODE_KEY, "OFF") == "QUIET") PauseMode.QUIET else PauseMode.OFF
    )
}

fun saveNotificationPrefs(ctx: Context, p: NotificationPrefs) {
    ctx.getSharedPreferences("b", 0).edit()
        .putBoolean(SILENT_ON_KEY, p.silentOn)
        .putInt(SILENT_START_KEY, p.silentStartMin)
        .putInt(SILENT_END_KEY, p.silentEndMin)
        .putLong(PAUSE_UNTIL_KEY, p.pauseUntilMs)
        .putString(PAUSE_MODE_KEY, p.pauseMode.name)
        .apply()
    publishState(ctx)
}
