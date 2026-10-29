package com.example.beeing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SuperDayTest {

    private val originalZone: TimeZone = TimeZone.getDefault()

    @After fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = Calendar.OCTOBER): Long =
        Calendar.getInstance().apply {
            set(2026, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun prefs(
        reminders: Boolean = true, silent: Boolean = false, start: Int = 22 * 60, end: Int = 7 * 60,
        pauseUntil: Long = 0L, mode: PauseMode = PauseMode.OFF
    ) = NotificationPrefs(reminders, silent, start, end, pauseUntil, mode)

    private fun hours(day: Int, from: Int, to: Int) = (from..to).map { at(day, it) }

    private fun tally(
        counted: List<Long>, beginner: Boolean = false, goal: Int = 8, isToday: Boolean = true
    ) = DayTally(
        dayStartMs = at(28, 0), countedStarts = counted, beginner = beginner, goal = goal,
        streakAfter = 0, earnedSaver = false, recovered = false, isToday = isToday
    )

    private val day28 = at(28, 0)

    @Test fun evening_ring_close_with_no_silent_hours() {
        val r = reachableHours(day28, hours(28, 9, 16), prefs(), at(28, 19, 30))
        assertEquals(7, r) // 17, 18 ratable; 19..23
    }

    @Test fun silent_hours_drop_future_hours_by_their_reminder_moment() {
        val r = reachableHours(day28, hours(28, 9, 16), prefs(silent = true), at(28, 19, 30))
        assertEquals(4, r) // 17, 18 ratable; 19, 20 (21 ends at 22:00, silent)
    }

    @Test fun silent_start_mid_hour_drops_the_hour_whose_end_is_silent() {
        // Silent from 22:30. 21:00 ends 22:00 (awake) so it stays; 22:00 ends 23:00 (silent) so it goes.
        val p = prefs(silent = true, start = 22 * 60 + 30, end = 7 * 60)
        val r = reachableHours(day28, hours(28, 9, 16), p, at(28, 19, 30))
        assertEquals(2 + 3, r) // 17, 18 ratable; 19, 20, 21
    }

    @Test fun ratable_hours_count_even_inside_silent_hours() {
        val r = reachableHours(day28, hours(28, 0, 20), prefs(silent = true), at(28, 23, 10))
        assertEquals(2, r) // 21:00 and 22:00; 23:00 ends at midnight, silent
    }

    @Test fun off_pause_drops_hours_quiet_pause_does_not() {
        val until = at(28, 22)
        val off = prefs(pauseUntil = until, mode = PauseMode.OFF)
        val quiet = prefs(pauseUntil = until, mode = PauseMode.QUIET)
        val now = at(28, 19, 30)
        // OFF: 19 ends 20:00 and 20 ends 21:00 are paused; 21 ends 22:00 (not > 22:00), 22, 23 stay.
        assertEquals(2 + 3, reachableHours(day28, hours(28, 9, 16), off, now))
        assertEquals(7, reachableHours(day28, hours(28, 9, 16), quiet, now))
    }

    @Test fun reminders_off_does_not_drop_hours() {
        val r = reachableHours(day28, hours(28, 9, 16), prefs(reminders = false), at(28, 19, 30))
        assertEquals(7, r)
    }

    @Test fun after_midnight_only_ratable_hours_of_yesterday_remain() {
        val r = reachableHours(day28, hours(28, 9, 16), prefs(), at(29, 0, 5))
        assertEquals(2, r) // 22:00 and 23:00
    }

    @Test fun dst_spring_forward_day_has_23_candidate_hours() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        val dayStart = at(29, 0, month = Calendar.MARCH)
        val r = reachableHours(dayStart, emptyList(), prefs(), dayStart)
        assertEquals(23, r)
    }

    @Test fun invite_threshold_needs_one_spare_hour() {
        val eight = tally(hours(28, 9, 16))
        assertEquals(8, eight.countedHours)
        assertNull(superInviteFor(eight, 2, 4))
        assertNull(superInviteFor(eight, 2, 3))
        assertNotNull(superInviteFor(eight, 2, 5))
        assertEquals(SuperInvite(hoursNeeded = 4, savers = 2), superInviteFor(eight, 2, 5))
        assertNull(superInviteFor(tally(hours(28, 9, 16), beginner = true, goal = 4), 2, 10))
        assertNull(superInviteFor(tally(hours(28, 7, 18)), 2, 10)) // already 12
    }

    @Test fun invite_flags_the_saver_cap() {
        val eight = tally(hours(28, 9, 16))
        assertEquals(false, superInviteFor(eight, SAVER_CAP - 1, 10)!!.atCap)
        assertEquals(true, superInviteFor(eight, SAVER_CAP, 10)!!.atCap)
    }

    @Test fun cue_uses_exact_fit() {
        val nine = tally(hours(28, 8, 16)) // 9 counted
        assertEquals(3, superCueFor(nine, 3))
        assertNull(superCueFor(nine, 2))
        assertNull(superCueFor(tally(hours(28, 9, 14)), 10)) // 6 < goal
        assertNull(superCueFor(tally(hours(28, 7, 18)), 10)) // already 12
        assertNull(superCueFor(tally(hours(28, 8, 16), beginner = true, goal = 4), 10))
        assertNull(superCueFor(null, 10))
    }

    @Test fun late_hint_for_the_super_hour_and_the_ring_hour() {
        val superEleven = tally(hours(28, 7, 17), isToday = false) // 11 counted
        val ringSeven = tally(hours(28, 9, 15), isToday = false)   // 7 counted, goal 8
        val missing = at(28, 21)
        assertEquals(" · finishes your Super day", lateHourHint(missing, listOf(superEleven)))
        assertEquals(" · closes yesterday's ring", lateHourHint(missing, listOf(ringSeven)))
        // already counted, today, beginner, other day, or too far from either -> null
        assertNull(lateHourHint(at(28, 9), listOf(superEleven)))
        assertNull(lateHourHint(missing, listOf(superEleven.copy(isToday = true))))
        assertNull(lateHourHint(missing, listOf(ringSeven.copy(beginner = true))))
        assertNull(lateHourHint(at(27, 21), listOf(superEleven)))
        assertNull(lateHourHint(missing, listOf(tally(hours(28, 9, 12), isToday = false)))) // 4 counted
    }

    @Test fun late_ring_close_gets_no_invite_by_the_normal_rule() {
        val yesterday = tally(hours(28, 9, 16), isToday = false) // 8 counted
        val reachable = reachableHours(day28, yesterday.countedStarts, prefs(), at(29, 0, 5))
        assertNull(superInviteFor(yesterday, 2, reachable))
    }
}
