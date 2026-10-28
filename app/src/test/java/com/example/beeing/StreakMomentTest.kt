package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakMomentTest {
    private fun s(
        hours: Int = 5, earned: Boolean = false, open: Boolean = false, recovered: Boolean = false,
        savers: Int = 2, streak: Int = 3, beginner: Boolean = false
    ) = StreakState(
        currentStreak = streak, displayStreak = streak, todayHours = hours, todayQualified = hours >= 8,
        extraToday = 0, beginnerMode = beginner, hoursRequired = 8, beginnerDaysCompleted = 0,
        savers = savers, recoveryOpen = open, recoveryDaysLeft = if (open) 2 else 0,
        todayEarnedSaver = earned, todayRecovered = recovered
    )

    @Test fun first_composition_never_fires() {
        assertNull(streakMoment(null, s(hours = 12, earned = true, savers = 3)))
    }

    @Test fun the_twelfth_hour_fires_saver_earned_once() {
        assertEquals(StreakMoment.SaverEarned(3), streakMoment(s(hours = 11), s(hours = 12, earned = true, savers = 3)))
        assertNull(streakMoment(s(hours = 12, earned = true, savers = 3), s(hours = 12, earned = true, savers = 3)))
    }

    @Test fun a_many_hour_jump_from_an_import_does_not_fire() {
        assertNull(streakMoment(s(hours = 2), s(hours = 12, earned = true, savers = 3)))
    }

    @Test fun a_mode_switch_with_no_new_hour_does_not_fire() {
        assertNull(streakMoment(s(hours = 12, beginner = true), s(hours = 12, earned = true, savers = 3)))
    }

    @Test fun the_tenth_hour_fires_recovered_only_when_the_window_closes_by_recovery() {
        val before = s(hours = 9, open = true, streak = 0)
        assertEquals(StreakMoment.Recovered(4), streakMoment(before, s(hours = 10, recovered = true, streak = 4)))
        assertNull(streakMoment(before, s(hours = 10, streak = 1)))
    }
}
