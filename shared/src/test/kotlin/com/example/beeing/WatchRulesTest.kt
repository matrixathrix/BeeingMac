package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WatchRulesTest {

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, day, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private var nextId = 1L
    private fun rating(day: Int, hour: Int, tags: List<String> = listOf("Work"), origin: Origin = Origin.PHONE): RatingEntry {
        val start = at(day, hour)
        return RatingEntry(id = start + 3_600_000L + nextId++, score = 7, timestamp = start, hourLabel = "",
            tags = tags, origin = origin)
    }

    private val prefs = NotificationPrefs(false, 22 * 60, 7 * 60, 0L, PauseMode.OFF)

    // --- waiting hours ----------------------------------------------------------

    @Test fun two_waiting_expiring_first() {
        assertEquals(listOf(at(10, 13), at(10, 14)), waitingHours(at(10, 15, 20), emptySet()))
    }

    @Test fun one_waiting_after_latest_rated() {
        assertEquals(listOf(at(10, 13)), waitingHours(at(10, 15, 20), setOf(at(10, 14))))
    }

    @Test fun after_midnight_yesterdays_last_hours_are_waiting() {
        assertEquals(listOf(at(9, 22), at(9, 23)), waitingHours(at(10, 0, 30), emptySet()))
    }

    // --- top tags ----------------------------------------------------------------

    @Test fun top_tags_in_user_order_not_by_count() {
        val ordered = listOf("A", "B", "C")
        val r = List(5) { rating(10, it, listOf("C")) } + List(2) { rating(9, it, listOf("A")) }
        assertEquals(listOf("A", "C"), topTags(r, ordered, at(10, 20), n = 2))
    }

    @Test fun top_tags_skip_reclaim_tag_and_old_uses() {
        val ordered = listOf("A", "B", "C")
        val old = rating(1, 9, listOf("A")).copy(timestamp = at(10, 9) - 31L * 24 * 3_600_000L)
        val r = listOf(old, old, rating(10, 1, listOf("B", RECLAIM_TAG)), rating(10, 2, listOf("C")))
        assertEquals(listOf("B", "C"), topTags(r, ordered, at(10, 20), n = 2))
    }

    @Test fun top_tags_fill_from_the_users_order_when_few_are_used() {
        val ordered = listOf("A", "B", "C", "D")
        assertEquals(listOf("A", "B", "D"), topTags(listOf(rating(10, 1, listOf("D"))), ordered, at(10, 20), n = 3))
    }

    // --- the watch's view -------------------------------------------------------------

    private fun stateAt(carryDay: Long, all: List<RatingEntry>, recent: List<RatingEntry>, merged: List<Long> = emptyList()) =
        PhoneState(0L, listOf("Work"), listOf("Work"), emptyList(), prefs,
            carryAt(all, emptyList(), carryDay), recent, merged)

    @Test fun not_set_up_without_state() {
        val v = watchView(null, emptyList(), at(10, 12))
        assertFalse(v.setUp)
        assertTrue(v.waiting.isEmpty())
    }

    @Test fun offline_three_days_matches_phone() {
        // The phone's history to 6 June, then the watch alone for 7, 8, 9 and today (10).
        val phone = (1..6).flatMap { d -> (8..15).map { rating(d, it) } }
        val watch = (7..9).flatMap { d -> (8..15).map { rating(d, it, origin = Origin.WATCH) } } +
            (8..10).map { rating(10, it, origin = Origin.WATCH) }
        val now = at(10, 12, 5)
        val view = watchView(stateAt(at(6, 0), phone, emptyList()), watch, now)
        val truth = computeStreakState(phone + watch, emptyList(), now)
        assertEquals(truth.displayStreak, view.streak)
        assertEquals(truth.savers, view.savers)
        assertEquals(3, view.todayHours)
        assertEquals(listOf(at(10, 11)), view.waiting)
    }

    @Test fun confirmed_false_while_local_unmerged() {
        val local = listOf(rating(10, 10, origin = Origin.WATCH))
        val s = stateAt(at(7, 0), emptyList(), emptyList())
        assertFalse(watchView(s, local, at(10, 11, 30)).confirmed)
        assertTrue(watchView(s.copy(mergedWatchIds = local.map { it.id }), local, at(10, 11, 30)).confirmed)
    }

    @Test fun local_save_wins_over_the_phones_copy_of_the_hour() {
        val phoneCopy = rating(10, 10).copy(score = 3)
        val mine = rating(10, 10, origin = Origin.WATCH).copy(score = 9)
        val v = watchView(stateAt(at(7, 0), emptyList(), listOf(phoneCopy)), listOf(mine), at(10, 11, 30))
        assertEquals(1, v.todayHours)
    }
}
