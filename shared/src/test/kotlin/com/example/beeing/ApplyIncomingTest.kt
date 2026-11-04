package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyIncomingTest {

    private val h = 3_600_000L
    private val hour = 1_793_000_000_000L - (1_793_000_000_000L % h)

    private fun watch(savedAt: Long) =
        RatingEntry(savedAt, 6, hour, "", tags = listOf("Work"), origin = Origin.WATCH)

    private fun phone(modifiedAt: Long, reclaimed: Boolean = false, ts: Long = hour) =
        RatingEntry(modifiedAt, 8, ts, "", tags = if (reclaimed) listOf(RECLAIM_TAG) else listOf("Rest"))

    @Test fun applies_new_hour() {
        val w = watch(hour + h + 60_000L)
        val other = phone(hour, ts = hour - h)
        val r = applyIncoming(w, listOf(other), emptyList(), emptyList())
        assertTrue(r.accepted)
        assertEquals(setOf(other, w), r.ratings.toSet())
    }

    @Test fun replaces_older_phone_entry() {
        val p = phone(hour + h + 60_000L)
        val w = watch(hour + h + 120_000L)
        assertEquals(listOf(w), applyIncoming(w, listOf(p), emptyList(), emptyList()).ratings)
    }

    @Test fun refunds_nearest_spend_for_replaced_reclaim() {
        val p = phone(hour + 2 * h + 30_000L, reclaimed = true)
        val w = watch(hour + h + 60_000L)
        val r = applyIncoming(w, listOf(p), emptyList(), listOf(hour - 20 * h, hour + 2 * h + 31_000L, hour + 5 * h))
        assertEquals(listOf(w), r.ratings)
        assertEquals(listOf(hour - 20 * h, hour + 5 * h), r.spends)
    }

    @Test fun redelivered_rating_is_idempotent() {
        val p = phone(hour + 2 * h + 30_000L, reclaimed = true)
        val w = watch(hour + h + 60_000L)
        val spends = listOf(hour + 2 * h + 31_000L, hour + 2 * h + 40_000L)
        val once = applyIncoming(w, listOf(p), emptyList(), spends)
        val twice = applyIncoming(w, once.ratings, emptyList(), once.spends)
        assertEquals(once.ratings, twice.ratings)
        assertEquals(1, twice.spends.size)
        assertTrue(twice.accepted)
    }

    @Test fun dropped_rating_changes_nothing() {
        val p = phone(hour + h + 120_000L)
        val r = applyIncoming(watch(hour + h + 60_000L), listOf(p), emptyList(), listOf(7L))
        assertFalse(r.accepted)
        assertEquals(listOf(p), r.ratings)
        assertEquals(listOf(7L), r.spends)
    }
}
