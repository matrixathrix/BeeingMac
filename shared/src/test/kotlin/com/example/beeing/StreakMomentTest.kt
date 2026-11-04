package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StreakMomentTest {
    private val hourMs = 3_600_000L
    private val minuteMs = 60_000L

    // Local midnights in June (no DST transition nearby in any zone), so the clock-hour
    // math in the ratable-hour guard lines up with `start + n * hourMs`.
    private fun midnight(day: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val d0 = midnight(10)
    private val dPrev = midnight(9)
    private val dNext = midnight(11)

    /** Five minutes after hour [hourIndex] of [start]'s day ended: that hour is the latest ratable one. */
    private fun justAfter(start: Long, hourIndex: Int) = start + (hourIndex + 1) * hourMs + 5 * minuteMs

    /** [hours] counted hours on [start]'s day, consecutive from clock hour [first]. */
    private fun tally(
        start: Long, hours: Int, beginner: Boolean = false, today: Boolean = true,
        earned: Boolean = false, recovered: Boolean = false, streak: Int = 3, first: Int = 0
    ) = DayTally(
        dayStartMs = start,
        countedStarts = List(hours) { start + (first + it) * hourMs },
        beginner = beginner,
        goal = if (beginner) BEGINNER_HOURS_REQUIRED else STREAK_HOURS_REQUIRED,
        streakAfter = streak,
        earnedSaver = earned,
        recovered = recovered,
        isToday = today
    )

    private fun state(
        vararg days: DayTally, savers: Int = 2, open: Boolean = false, streak: Int = 3,
        beginnerDays: Int = 0
    ): StreakState {
        val today = days.lastOrNull { it.isToday }
        val hours = today?.countedHours ?: 0
        val beginner = today?.beginner ?: false
        val goal = today?.goal ?: STREAK_HOURS_REQUIRED
        return StreakState(
            currentStreak = streak, displayStreak = streak, todayHours = hours, todayQualified = hours >= goal,
            extraToday = maxOf(0, hours - goal), beginnerMode = beginner, hoursRequired = goal,
            beginnerDaysCompleted = beginnerDays, savers = savers, recoveryOpen = open,
            recoveryDaysLeft = if (open) 2 else 0, todayEarnedSaver = today?.earnedSaver ?: false,
            todayRecovered = today?.recovered ?: false, recentDays = days.toList()
        )
    }

    @Test fun first_composition_never_fires() {
        val now = state(tally(d0, 12, earned = true), savers = 3)
        val t = justAfter(d0, 11)
        assertNull(streakMoment(null, now, t))
        assertNull(ringClosed(null, state(tally(d0, 8)), justAfter(d0, 7)))
        assertNull(singleNewHourDay(null, now, t))
    }

    @Test fun twelfth_hour_fires_super_day_once() {
        val now = state(tally(d0, 12, earned = true), savers = 3)
        val t = justAfter(d0, 11)
        assertEquals(StreakMoment.SuperDay(3, true, false), streakMoment(state(tally(d0, 11)), now, t))
        assertNull(streakMoment(now, now, t))
    }

    @Test fun twelfth_hour_in_the_grace_hour_fires_too() {
        // 11:00 rated at 13:05: the expiring hour (offset 1) is still ratable.
        val now = state(tally(d0, 12, earned = true), savers = 3)
        assertEquals(
            StreakMoment.SuperDay(3, true, false),
            streakMoment(state(tally(d0, 11)), now, d0 + 13 * hourMs + 5 * minuteMs)
        )
    }

    @Test fun twelfth_hour_at_cap_fires_super_day_without_saver() {
        assertEquals(
            StreakMoment.SuperDay(5, false, false),
            streakMoment(
                state(tally(d0, 11), savers = 5), state(tally(d0, 12, earned = false), savers = 5),
                justAfter(d0, 11)
            )
        )
    }

    @Test fun late_twelfth_hour_after_midnight_is_late() {
        // 23:00 saved at 00:05: the latest completed hour, still ratable.
        val prev = state(tally(dPrev, 11, today = false, first = 12), tally(d0, 0))
        val now = state(tally(dPrev, 12, today = false, earned = true, first = 12), tally(d0, 0), savers = 3)
        assertEquals(StreakMoment.SuperDay(3, true, true), streakMoment(prev, now, d0 + 5 * minuteMs))
    }

    @Test fun midnight_rollover_without_a_save_never_fires() {
        val prev = state(tally(dPrev, 5, today = false), tally(d0, 11, first = 12))
        val now = state(tally(d0, 11, today = false, first = 12), tally(dNext, 0))
        val t = dNext + 5 * minuteMs
        assertNull(streakMoment(prev, now, t))
        assertNull(ringClosed(prev, now, t))
    }

    @Test fun app_open_across_midnight_then_late_save_fires() {
        val prev = state(tally(dPrev, 5, today = false), tally(d0, 11, first = 12))
        val now = state(tally(d0, 12, today = false, earned = true, first = 12), tally(dNext, 0), savers = 3)
        assertEquals(StreakMoment.SuperDay(3, true, true), streakMoment(prev, now, dNext + 5 * minuteMs))
    }

    @Test fun import_jump_never_fires() {
        assertNull(
            streakMoment(state(tally(d0, 2)), state(tally(d0, 12, earned = true), savers = 3), justAfter(d0, 11))
        )
    }

    @Test fun two_days_each_plus_one_never_fires() {
        val prev = state(tally(dPrev, 7, today = false, first = 16), tally(d0, 11))
        val now = state(tally(dPrev, 8, today = false, first = 16), tally(d0, 12, earned = true), savers = 3)
        val t = justAfter(d0, 11)
        assertNull(singleNewHourDay(prev, now, t))
        assertNull(streakMoment(prev, now, t))
        assertNull(ringClosed(prev, now, t))
    }

    @Test fun mode_toggle_without_new_hour_never_fires() {
        val t = justAfter(d0, 11)
        val prev = state(tally(d0, 4, beginner = false))
        val now = state(tally(d0, 4, beginner = true), beginnerDays = 1)
        assertNull(streakMoment(prev, now, t))
        assertNull(ringClosed(prev, now, t))
        val prev12 = state(tally(d0, 12, beginner = true))
        val now12 = state(tally(d0, 12, beginner = false, earned = true), savers = 3)
        assertNull(streakMoment(prev12, now12, t))
        assertNull(ringClosed(prev12, now12, t))
    }

    @Test fun a_day_only_in_prev_is_ignored_and_a_new_day_counts_from_zero() {
        // Yesterday dropped off the snapshot; today appeared fresh with its first hour.
        val prev = state(tally(dPrev, 5, today = false))
        val now = state(tally(d0, 1))
        assertEquals(tally(d0, 1), singleNewHourDay(prev, now, justAfter(d0, 0)))
    }

    @Test fun eighth_hour_closes_the_ring() {
        val now = state(tally(d0, 8, streak = 6))
        val closed = ringClosed(state(tally(d0, 7)), now, justAfter(d0, 7))
        assertEquals(RingClosed(tally(d0, 8, streak = 6), count = 6, beginner = false, late = false), closed)
        // The ninth hour is past the goal: no second ring close.
        assertNull(ringClosed(now, state(tally(d0, 9, streak = 6)), justAfter(d0, 8)))
    }

    @Test fun late_eighth_hour_closes_yesterdays_ring() {
        val prev = state(tally(dPrev, 7, today = false, first = 16), tally(d0, 0))
        val now = state(tally(dPrev, 8, today = false, streak = 6, first = 16), tally(d0, 0))
        val closed = ringClosed(prev, now, d0 + 5 * minuteMs)
        assertEquals(6, closed?.count)
        assertTrue(closed!!.late)
    }

    @Test fun beginner_fourth_hour_counts_beginner_days() {
        val closed = ringClosed(
            state(tally(d0, 3, beginner = true), beginnerDays = 2),
            state(tally(d0, 4, beginner = true, streak = 9), beginnerDays = 3),
            justAfter(d0, 3)
        )
        assertEquals(3, closed?.count)
        assertTrue(closed!!.beginner)
        assertEquals(false, closed.late)
    }

    @Test fun beginner_twelfth_hour_is_not_a_super_day() {
        assertNull(
            streakMoment(
                state(tally(d0, 11, beginner = true)), state(tally(d0, 12, beginner = true)), justAfter(d0, 11)
            )
        )
    }

    @Test fun tenth_hour_recovers_only_when_window_closes_by_recovery() {
        val t = justAfter(d0, 9)
        val before = state(tally(d0, 9, streak = 0), open = true, streak = 0)
        assertEquals(
            StreakMoment.Recovered(4),
            streakMoment(before, state(tally(d0, 10, recovered = true, streak = 4), streak = 4), t)
        )
        assertNull(streakMoment(before, state(tally(d0, 10, streak = 1), streak = 1), t))
    }

    // Delete + Undo restores an entry with its old timestamp. From the Past journal at
    // 15:00 that is +1 counted hour on a day — but not on a ratable hour, so no takeover.
    @Test fun restored_old_yesterday_hour_never_fires() {
        val t = d0 + 15 * hourMs
        val today = tally(d0, 5, first = 9)
        // Yesterday 7 -> 8: hour 23:00 deleted and restored.
        val ringPrev = state(tally(dPrev, 7, today = false, first = 16), today)
        val ringNow = state(tally(dPrev, 8, today = false, streak = 6, first = 16), today)
        assertNull(ringClosed(ringPrev, ringNow, t))
        assertNull(singleNewHourDay(ringPrev, ringNow, t))
        // Yesterday 11 -> 12.
        val superPrev = state(tally(dPrev, 11, today = false, first = 12), today)
        val superNow = state(tally(dPrev, 12, today = false, earned = true, first = 12), today, savers = 3)
        assertNull(streakMoment(superPrev, superNow, t))
        assertNull(ringClosed(superPrev, superNow, t))
    }

    @Test fun restored_old_today_hour_never_fires() {
        val t = d0 + 15 * hourMs // ratable: 13:00 and 14:00
        // 7 -> 8 via 07:00.
        assertNull(ringClosed(state(tally(d0, 7)), state(tally(d0, 8, streak = 6)), t))
        // 11 -> 12 via 11:00.
        assertNull(streakMoment(state(tally(d0, 11)), state(tally(d0, 12, earned = true), savers = 3), t))
    }

    @Test fun pure_delete_returns_null() {
        val t = justAfter(d0, 11)
        val prev = state(tally(d0, 12, earned = true), savers = 3)
        val now = state(tally(d0, 11), savers = 2)
        assertNull(singleNewHourDay(prev, now, t))
        assertNull(streakMoment(prev, now, t))
        assertNull(ringClosed(prev, now, t))
    }
}
