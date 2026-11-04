package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationPrefsTest {

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun prefs(
        reminders: Boolean = true, silent: Boolean = false, start: Int = 22 * 60, end: Int = 7 * 60,
        pauseUntil: Long = 0L, mode: PauseMode = PauseMode.OFF
    ) = NotificationPrefs(reminders, silent, start, end, pauseUntil, mode)

    private fun state(streak: Int, savers: Int, beginner: Boolean = false) = StreakState(
        currentStreak = streak, displayStreak = streak, todayHours = 0, todayQualified = false, extraToday = 0,
        beginnerMode = beginner, hoursRequired = if (beginner) 4 else 8, beginnerDaysCompleted = 0,
        savers = savers, recoveryOpen = false, recoveryDaysLeft = 0, todayEarnedSaver = false
    )

    @Test fun silent_hours_cross_midnight() {
        val p = prefs(silent = true)
        assertTrue(inSilentHours(p, at(28, 23, 30)))
        assertTrue(inSilentHours(p, at(28, 6, 59)))
        assertFalse(inSilentHours(p, at(28, 7, 0)))
        assertFalse(inSilentHours(p, at(28, 12, 0)))
        assertTrue(inSilentHours(p, at(28, 22, 0)))
    }

    @Test fun silent_hours_within_one_day() {
        val p = prefs(silent = true, start = 13 * 60, end = 15 * 60)
        assertTrue(inSilentHours(p, at(28, 14, 0)))
        assertFalse(inSilentHours(p, at(28, 15, 0)))
        assertFalse(inSilentHours(p, at(28, 12, 59)))
    }

    @Test fun equal_start_end_means_off() {
        val p = prefs(silent = true, start = 9 * 60, end = 9 * 60)
        assertFalse(inSilentHours(p, at(28, 9, 0)))
        assertFalse(inSilentHours(p, at(28, 3, 0)))
    }

    @Test fun silent_switch_off_means_never_silent() {
        assertFalse(inSilentHours(prefs(silent = false), at(28, 23, 0)))
    }

    @Test fun pause_off_suppresses_hourly_only() {
        val now = at(28, 20)
        val p = prefs(pauseUntil = at(29, 20), mode = PauseMode.OFF)
        assertEquals(Delivery.NONE, hourlyDelivery(p, now))
        assertEquals(Delivery.SOUND, otherDelivery(p, now))
    }

    @Test fun pause_quiet_is_silent() {
        val now = at(28, 20)
        val p = prefs(pauseUntil = at(29, 20), mode = PauseMode.QUIET)
        assertEquals(Delivery.SILENT, hourlyDelivery(p, now))
    }

    @Test fun expired_pause_is_ignored() {
        val p = prefs(pauseUntil = at(28, 20), mode = PauseMode.OFF)
        assertEquals(Delivery.SOUND, hourlyDelivery(p, at(28, 20, 1)))
        assertEquals(Delivery.SOUND, hourlyDelivery(p, at(30, 9)))
    }

    @Test fun reminders_off_is_none() {
        assertEquals(Delivery.NONE, hourlyDelivery(prefs(reminders = false), at(28, 12)))
        assertEquals(Delivery.SOUND, otherDelivery(prefs(reminders = false), at(28, 12)))
    }

    @Test fun silent_hours_make_hourly_and_other_silent_but_never_none() {
        val p = prefs(silent = true)
        assertEquals(Delivery.SILENT, hourlyDelivery(p, at(28, 23)))
        assertEquals(Delivery.SILENT, otherDelivery(p, at(28, 23)))
    }

    @Test fun max_pause_is_48h() {
        val now = at(28, 20, 17)
        val max = maxPauseUntil(now)
        assertTrue(max <= now + 48 * 3_600_000L)
        assertTrue(max > now + 47 * 3_600_000L)
        assertEquals(0, Calendar.getInstance().apply { timeInMillis = max }.get(Calendar.MINUTE))
    }

    @Test fun pause_risk_counts_overlapped_days_minus_qualified_today() {
        val now = at(28, 20)             // Wednesday
        val until = at(30, 21)           // Friday 9 PM
        val open = pauseRisk(state(streak = 5, savers = 1), false, false, now, until)!!
        assertEquals(3, open.days)
        assertEquals(at(29, 0), open.breakDayStartMs)
        val done = pauseRisk(state(streak = 5, savers = 1), true, false, now, until)!!
        assertEquals(2, done.days)
        assertEquals(at(30, 0), done.breakDayStartMs)
    }

    @Test fun pause_risk_null_when_savers_cover() {
        assertNull(pauseRisk(state(streak = 5, savers = 3), false, false, at(28, 20), at(30, 21)))
    }

    @Test fun pause_risk_fade_version_at_streak_zero() {
        val r = pauseRisk(state(streak = 0, savers = 2), false, false, at(28, 20), at(30, 21))
        assertNotNull(r)
        assertEquals(2, r!!.fadeCount)
        assertNull(r.breakDayStartMs)
        assertNull(pauseRisk(state(streak = 0, savers = 0), false, false, at(28, 20), at(30, 21)))
    }

    @Test fun pause_risk_ignores_beginner() {
        assertNull(pauseRisk(state(streak = 5, savers = 0, beginner = true), false, true, at(28, 20), at(30, 21)))
    }

    @Test fun pause_ending_at_midnight_does_not_overlap_that_day() {
        val r = pauseRisk(state(streak = 5, savers = 0), false, false, at(28, 20), at(30, 0))
        assertEquals(2, r!!.days)
    }
}
