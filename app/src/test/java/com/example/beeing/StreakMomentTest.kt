package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakMomentTest {
    private val hourMs = 3_600_000L
    private val dayMs = 24 * hourMs
    private val d0 = 1_800_000_000_000L // any fixed day start; only the keying matters here
    private val dPrev = d0 - dayMs
    private val dNext = d0 + dayMs

    private fun tally(
        start: Long, hours: Int, beginner: Boolean = false, today: Boolean = true,
        earned: Boolean = false, recovered: Boolean = false, streak: Int = 3
    ) = DayTally(
        dayStartMs = start,
        countedStarts = List(hours) { start + it * hourMs },
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
        assertNull(streakMoment(null, now))
        assertNull(ringClosed(null, state(tally(d0, 8))))
        assertNull(singleNewHourDay(null, now))
    }

    @Test fun twelfth_hour_fires_super_day_once() {
        val now = state(tally(d0, 12, earned = true), savers = 3)
        assertEquals(StreakMoment.SuperDay(3, true, false), streakMoment(state(tally(d0, 11)), now))
        assertNull(streakMoment(now, now))
    }

    @Test fun twelfth_hour_at_cap_fires_super_day_without_saver() {
        assertEquals(
            StreakMoment.SuperDay(5, false, false),
            streakMoment(state(tally(d0, 11), savers = 5), state(tally(d0, 12, earned = false), savers = 5))
        )
    }

    @Test fun late_twelfth_hour_after_midnight_is_late() {
        val prev = state(tally(dPrev, 11, today = false), tally(d0, 0))
        val now = state(tally(dPrev, 12, today = false, earned = true), tally(d0, 0), savers = 3)
        assertEquals(StreakMoment.SuperDay(3, true, true), streakMoment(prev, now))
    }

    @Test fun midnight_rollover_without_a_save_never_fires() {
        val prev = state(tally(dPrev, 5, today = false), tally(d0, 11))
        val now = state(tally(d0, 11, today = false), tally(dNext, 0))
        assertNull(streakMoment(prev, now))
        assertNull(ringClosed(prev, now))
    }

    @Test fun app_open_across_midnight_then_late_save_fires() {
        val prev = state(tally(dPrev, 5, today = false), tally(d0, 11))
        val now = state(tally(d0, 12, today = false, earned = true), tally(dNext, 0), savers = 3)
        assertEquals(StreakMoment.SuperDay(3, true, true), streakMoment(prev, now))
    }

    @Test fun import_jump_never_fires() {
        assertNull(streakMoment(state(tally(d0, 2)), state(tally(d0, 12, earned = true), savers = 3)))
    }

    @Test fun two_days_each_plus_one_never_fires() {
        val prev = state(tally(dPrev, 7, today = false), tally(d0, 11))
        val now = state(tally(dPrev, 8, today = false), tally(d0, 12, earned = true), savers = 3)
        assertNull(singleNewHourDay(prev, now))
        assertNull(streakMoment(prev, now))
        assertNull(ringClosed(prev, now))
    }

    @Test fun mode_toggle_without_new_hour_never_fires() {
        val prev = state(tally(d0, 4, beginner = false))
        val now = state(tally(d0, 4, beginner = true), beginnerDays = 1)
        assertNull(streakMoment(prev, now))
        assertNull(ringClosed(prev, now))
        val prev12 = state(tally(d0, 12, beginner = true))
        val now12 = state(tally(d0, 12, beginner = false, earned = true), savers = 3)
        assertNull(streakMoment(prev12, now12))
        assertNull(ringClosed(prev12, now12))
    }

    @Test fun a_day_only_in_prev_is_ignored_and_a_new_day_counts_from_zero() {
        // Yesterday dropped off the snapshot; today appeared fresh with its first hour.
        val prev = state(tally(dPrev, 5, today = false))
        val now = state(tally(d0, 1))
        assertEquals(tally(d0, 1), singleNewHourDay(prev, now))
    }

    @Test fun eighth_hour_closes_the_ring() {
        val now = state(tally(d0, 8, streak = 6))
        val closed = ringClosed(state(tally(d0, 7)), now)
        assertEquals(RingClosed(tally(d0, 8, streak = 6), count = 6, beginner = false, late = false), closed)
        // The ninth hour is past the goal: no second ring close.
        assertNull(ringClosed(now, state(tally(d0, 9, streak = 6))))
    }

    @Test fun late_eighth_hour_closes_yesterdays_ring() {
        val prev = state(tally(dPrev, 7, today = false), tally(d0, 0))
        val now = state(tally(dPrev, 8, today = false, streak = 6), tally(d0, 0))
        val closed = ringClosed(prev, now)
        assertEquals(6, closed?.count)
        assertTrue(closed!!.late)
    }

    @Test fun beginner_fourth_hour_counts_beginner_days() {
        val closed = ringClosed(
            state(tally(d0, 3, beginner = true), beginnerDays = 2),
            state(tally(d0, 4, beginner = true, streak = 9), beginnerDays = 3)
        )
        assertEquals(3, closed?.count)
        assertTrue(closed!!.beginner)
        assertEquals(false, closed.late)
    }

    @Test fun beginner_twelfth_hour_is_not_a_super_day() {
        assertNull(streakMoment(state(tally(d0, 11, beginner = true)), state(tally(d0, 12, beginner = true))))
    }

    @Test fun tenth_hour_recovers_only_when_window_closes_by_recovery() {
        val before = state(tally(d0, 9, streak = 0), open = true, streak = 0)
        assertEquals(
            StreakMoment.Recovered(4),
            streakMoment(before, state(tally(d0, 10, recovered = true, streak = 4), streak = 4))
        )
        assertNull(streakMoment(before, state(tally(d0, 10, streak = 1), streak = 1)))
    }
}
