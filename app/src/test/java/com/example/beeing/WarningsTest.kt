package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningsTest {
    private fun ev(
        type: StreakEventType, streak: Int = 0, savers: Int = 0, daysLeft: Int = 0,
        ts: Long = 1000L, day: Long = 1000L
    ) = StreakEvent("${type.name}:$day:$daysLeft", ts, type, streak, savers, daysLeft, day)

    private val label = { _: Long -> "Mon" }
    private fun one(e: StreakEvent) = warningsFrom(listOf(e), 0L, label).single()

    @Test fun copy_matches_spec() {
        one(ev(StreakEventType.SAVER_USED, streak = 12, savers = 1)).let {
            assertEquals("A streak saver kept your streak alive", it.title)
            assertEquals("You missed Mon. Your 12-day streak is safe. 1 saver left.", it.detail)
        }
        assertEquals("You missed Mon. Your 12-day streak is safe. 2 savers left.",
            one(ev(StreakEventType.SAVER_USED, streak = 12, savers = 2)).detail)
        one(ev(StreakEventType.SAVER_FADED, savers = 1)).let {
            assertEquals("A streak saver faded", it.title)
            assertEquals("No streak was running on Mon. Start one today to keep the 1 you have left.", it.detail)
        }
        assertEquals("No streak was running on Mon. That was your last saver.",
            one(ev(StreakEventType.SAVER_FADED, savers = 0)).detail)
        one(ev(StreakEventType.COMEBACK_GIFT, savers = 2)).let {
            assertEquals("2 streak savers on the house", it.title)
            assertEquals("Rate a few hours today to start a new streak.", it.detail)
        }
        one(ev(StreakEventType.LOW_SAVERS, savers = 1)).let {
            assertEquals("Only 1 saver left", it.title)
            assertEquals("Rate 12 hours in one day to earn another.", it.detail)
        }
        assertEquals("Only 2 savers left", one(ev(StreakEventType.LOW_SAVERS, savers = 2)).title)
        one(ev(StreakEventType.STREAK_BROKE, streak = 14)).let {
            assertEquals("Your 14-day streak broke", it.title)
            assertEquals("Rate 10 hours on one of the next 3 days to win it back.", it.detail)
        }
        one(ev(StreakEventType.RECOVERY_DAYS_LEFT, streak = 14, daysLeft = 2)).let {
            assertEquals("Only 2 days left to win back your 14-day streak", it.title)
            assertEquals("Rate 10 hours on one of them.", it.detail)
        }
        assertEquals("Only 1 day left to win back your 14-day streak",
            one(ev(StreakEventType.RECOVERY_DAYS_LEFT, streak = 14, daysLeft = 1)).title)
        one(ev(StreakEventType.RECOVERED, streak = 15)).let {
            assertEquals("Streak recovered", it.title)
            assertEquals("Your 15-day streak is back.", it.detail)
        }
    }

    @Test fun non_warning_types_are_excluded() {
        val all = StreakEventType.values().map { ev(it, ts = 5000L + it.ordinal, day = it.ordinal.toLong()) }
        val kept = warningsFrom(all, 0L, label).map { it.type }.toSet()
        assertEquals(
            setOf(
                StreakEventType.SAVER_USED, StreakEventType.SAVER_FADED, StreakEventType.LOW_SAVERS,
                StreakEventType.COMEBACK_GIFT, StreakEventType.STREAK_BROKE,
                StreakEventType.RECOVERY_DAYS_LEFT, StreakEventType.RECOVERED
            ),
            kept
        )
    }

    @Test fun newest_thirty_only_newest_first() {
        val many = (1..40).map { ev(StreakEventType.SAVER_USED, ts = it.toLong(), day = it.toLong()) }
        val w = warningsFrom(many, 0L, label)
        assertEquals(30, w.size)
        assertEquals(40L, w.first().timestamp)
    }

    @Test fun events_before_baseline_are_read() {
        val w = warningsFrom(
            listOf(ev(StreakEventType.SAVER_USED, ts = 100L, day = 1L), ev(StreakEventType.SAVER_USED, ts = 900L, day = 2L)),
            baselineMs = 500L, label
        )
        assertEquals(2, w.size)
        assertEquals(1, unreadCount(w, emptySet(), 500L))
    }

    @Test fun days_left_items_are_one_per_day() {
        val events = listOf(
            ev(StreakEventType.RECOVERY_DAYS_LEFT, daysLeft = 2, day = 1L, ts = 10L),
            ev(StreakEventType.RECOVERY_DAYS_LEFT, daysLeft = 1, day = 2L, ts = 20L)
        )
        val a = warningsFrom(events, 0L, label).map { it.id }
        val b = warningsFrom(events, 0L, label).map { it.id }
        assertEquals(2, a.toSet().size)
        assertEquals(a, b)
    }

    @Test fun unread_count_ignores_read_ids() {
        val w = warningsFrom(
            listOf(ev(StreakEventType.SAVER_USED, ts = 900L, day = 1L), ev(StreakEventType.SAVER_FADED, ts = 950L, day = 2L)),
            0L, label
        )
        assertEquals(2, unreadCount(w, emptySet(), 0L))
        assertEquals(1, unreadCount(w, setOf(w.first().id), 0L))
        assertTrue(unreadCount(w, w.map { it.id }.toSet(), 0L) == 0)
        assertFalse(w.isEmpty())
    }
}
