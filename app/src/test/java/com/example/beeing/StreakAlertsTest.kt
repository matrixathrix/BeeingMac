package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakAlertsTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun ev(type: StreakEventType, ts: Long, streak: Int = 0, savers: Int = 0, id: String = "${type.name}:$ts") =
        StreakEvent(id, ts, type, streak, savers, 0, ts - hour)

    @Test fun only_gift_used_faded_are_alerted() {
        val all = StreakEventType.values().mapIndexed { i, t -> ev(t, 1000L + i) }
        val posted = alertsToPost(all, emptySet(), 0L).map { it.type }.toSet()
        assertEquals(
            setOf(StreakEventType.COMEBACK_GIFT, StreakEventType.SAVER_USED, StreakEventType.SAVER_FADED),
            posted
        )
    }

    @Test fun nothing_before_baseline() {
        val e = ev(StreakEventType.SAVER_USED, 500L)
        assertTrue(alertsToPost(listOf(e), emptySet(), 501L).isEmpty())
        assertEquals(1, alertsToPost(listOf(e), emptySet(), 500L).size)
    }

    @Test fun each_event_once() {
        val a = ev(StreakEventType.SAVER_USED, 1000L)
        val b = ev(StreakEventType.SAVER_FADED, 2000L)
        assertEquals(listOf(b), alertsToPost(listOf(a, b), setOf(a.id), 0L))
        assertTrue(alertsToPost(listOf(a, b), setOf(a.id, b.id), 0L).isEmpty())
    }

    @Test fun oldest_first() {
        val a = ev(StreakEventType.SAVER_USED, 3000L)
        val b = ev(StreakEventType.SAVER_FADED, 1000L)
        assertEquals(listOf(b, a), alertsToPost(listOf(a, b), emptySet(), 0L))
    }

    @Test fun copy_matches_spec() {
        assertEquals(
            "2 streak savers on the house" to "Rate a few hours today to start a new streak.",
            alertCopy(ev(StreakEventType.COMEBACK_GIFT, 1L, savers = 2), false)
        )
        assertEquals(
            "A streak saver kept your 12-day streak alive" to "1 left.",
            alertCopy(ev(StreakEventType.SAVER_USED, 1L, streak = 12, savers = 1), false)
        )
        assertEquals(
            "A streak saver faded" to "One of your gifted savers faded. Rate today to keep the last one.",
            alertCopy(ev(StreakEventType.SAVER_FADED, 1L, savers = 1), true)
        )
        assertEquals(
            "A streak saver faded" to "Your last saver faded.",
            alertCopy(ev(StreakEventType.SAVER_FADED, 1L, savers = 0), true)
        )
        // a fade that wasn't a gift uses the bell wording
        val general = alertCopy(ev(StreakEventType.SAVER_FADED, 1L, savers = 0), false)
        assertEquals("A streak saver faded", general.first)
        assertTrue(general.second.endsWith("That was your last saver."))
    }

    @Test fun gifted_fade_detection() {
        val gift = ev(StreakEventType.COMEBACK_GIFT, 10 * day, savers = 2)
        val day8 = ev(StreakEventType.SAVER_FADED, 11 * day, savers = 1)
        val day9 = ev(StreakEventType.SAVER_FADED, 12 * day, savers = 0)
        val later = ev(StreakEventType.SAVER_FADED, 20 * day, savers = 0)
        val events = listOf(gift, day8, day9, later)
        assertTrue(isGiftedFade(events, day8))
        assertTrue(isGiftedFade(events, day9))
        assertFalse(isGiftedFade(events, later))
        assertFalse(isGiftedFade(listOf(day8), day8))
    }
}
