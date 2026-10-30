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

    private fun hours(day: Int, from: Int, to: Int) = (from..to).map { at(day, it) }

    private fun tally(
        counted: List<Long>, beginner: Boolean = false, goal: Int = 8, isToday: Boolean = true
    ) = DayTally(
        dayStartMs = at(28, 0), countedStarts = counted, beginner = beginner, goal = goal,
        streakAfter = 0, earnedSaver = false, recovered = false, isToday = isToday
    )

    private val day28 = at(28, 0)

    @Test fun evening_ring_close_counts_every_hour_left() {
        val r = reachableHours(day28, hours(28, 9, 16), at(28, 19, 30))
        assertEquals(7, r) // 17, 18 ratable; 19..23, whatever the reminder settings
    }

    @Test fun ring_close_at_8_50pm_gets_the_invite() {
        // The owner's 30 Oct: 8 hours, the ring closed by the 7pm hour at 20:50.
        val eight = hours(28, 0, 1) + hours(28, 7, 10) + hours(28, 18, 19)
        val r = reachableHours(day28, eight, at(28, 20, 50))
        assertEquals(4, r) // 20, 21, 22, 23 to come; 18 and 19 are counted
        assertEquals(false, superInviteFor(tally(eight), 2, r)!!.tooLate)
    }

    @Test fun ring_close_after_9pm_is_too_late() {
        val eight = hours(28, 0, 1) + hours(28, 7, 10) + hours(28, 19, 20)
        val r = reachableHours(day28, eight, at(28, 21, 10))
        assertEquals(3, r) // 21, 22, 23
        assertEquals(true, superInviteFor(tally(eight), 2, r)!!.tooLate)
    }

    @Test fun after_midnight_only_ratable_hours_of_yesterday_remain() {
        val r = reachableHours(day28, hours(28, 9, 16), at(29, 0, 5))
        assertEquals(2, r) // 22:00 and 23:00
    }

    @Test fun dst_spring_forward_day_has_23_candidate_hours() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        val dayStart = at(29, 0, month = Calendar.MARCH)
        val r = reachableHours(dayStart, emptyList(), dayStart)
        assertEquals(23, r)
    }

    @Test fun invite_always_shows_and_says_when_the_hours_dont_fit() {
        val eight = tally(hours(28, 9, 16))
        assertEquals(8, eight.countedHours)
        assertEquals(SuperInvite(hoursNeeded = 4, savers = 2, dayStartMs = day28), superInviteFor(eight, 2, 4))
        assertEquals(
            SuperInvite(hoursNeeded = 4, savers = 2, dayStartMs = day28, tooLate = true),
            superInviteFor(eight, 2, 3)
        )
        assertEquals(true, superInviteFor(eight, 2, 0)!!.tooLate)
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

    @Test fun yesterdays_ring_closed_after_midnight_gets_no_screen() {
        val yesterday = tally(hours(28, 9, 16), isToday = false) // 8 counted
        val reachable = reachableHours(day28, yesterday.countedStarts, at(29, 0, 5))
        assertNull(superInviteFor(yesterday, 2, reachable))
    }

    @Test fun invite_flag_blocks_a_second_invite_the_same_day() {
        val inv = SuperInvite(hoursNeeded = 4, savers = 2, dayStartMs = day28)
        val d = day28
        assertNull(gateInvite(inv, d, shownDay = d))
        assertEquals(inv, gateInvite(inv, d, shownDay = d - 24 * 3_600_000L))
        assertNull(gateInvite(null, d, shownDay = -1L))
    }
}
