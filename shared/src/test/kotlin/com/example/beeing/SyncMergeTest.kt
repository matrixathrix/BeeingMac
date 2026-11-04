package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergeTest {

    private val hour = 1_793_000_000_000L - (1_793_000_000_000L % 3_600_000L) // an hour start
    private val h = 3_600_000L

    /** A watch rating of [hour] saved at [savedAt]. */
    private fun watch(savedAt: Long, modifiedAt: Long = savedAt) =
        RatingEntry(savedAt, 6, hour, "", tags = listOf("Work"), modifiedAt = modifiedAt, origin = Origin.WATCH)

    private fun phone(modifiedAt: Long, reclaimed: Boolean = false) =
        RatingEntry(modifiedAt, 8, hour, "", tags = if (reclaimed) listOf(RECLAIM_TAG, "Friends") else listOf("Friends"),
            modifiedAt = modifiedAt)

    @Test fun drops_a_rating_saved_outside_its_window() {
        assertEquals(MergeResult.Drop, mergeIncoming(watch(hour + 4 * h), emptyList(), emptyList()))
        assertEquals(MergeResult.Drop, mergeIncoming(watch(hour + 30 * 60_000L), emptyList(), emptyList()))
    }

    @Test fun accepts_within_clock_slack() {
        assertEquals(MergeResult.Apply(null, null), mergeIncoming(watch(hour + 3 * h + 4 * 60_000L), emptyList(), emptyList()))
        assertEquals(MergeResult.Apply(null, null), mergeIncoming(watch(hour + h - 4 * 60_000L), emptyList(), emptyList()))
    }

    @Test fun later_save_wins() {
        val p = phone(hour + h + 60_000L)
        assertEquals(MergeResult.Apply(p, null), mergeIncoming(watch(hour + h + 120_000L), listOf(p), emptyList()))
    }

    @Test fun earlier_watch_save_loses() {
        val p = phone(hour + h + 120_000L)
        assertEquals(MergeResult.Drop, mergeIncoming(watch(hour + h + 60_000L), listOf(p), emptyList()))
    }

    @Test fun tie_goes_to_phone() {
        val p = phone(hour + h + 60_000L)
        assertEquals(MergeResult.Drop, mergeIncoming(watch(hour + h + 60_000L), listOf(p), emptyList()))
    }

    @Test fun live_watch_rating_beats_added_later_and_refunds() {
        val p = phone(hour + 2 * h, reclaimed = true)
        assertEquals(MergeResult.Apply(p, p.id), mergeIncoming(watch(hour + h + 60_000L), listOf(p), emptyList()))
    }

    @Test fun tombstone_newer_than_save_drops() {
        val t = Tombstone(hour, hour + h + 120_000L)
        assertEquals(MergeResult.Drop, mergeIncoming(watch(hour + h + 60_000L), emptyList(), listOf(t)))
    }

    @Test fun tombstone_older_than_save_applies() {
        val t = Tombstone(hour, hour + h + 60_000L)
        assertEquals(MergeResult.Apply(null, null), mergeIncoming(watch(hour + h + 120_000L), emptyList(), listOf(t)))
    }

    @Test fun another_hours_entry_is_not_a_conflict() {
        val other = phone(hour + h).copy(timestamp = hour - h)
        assertEquals(MergeResult.Apply(null, null), mergeIncoming(watch(hour + h + 60_000L), listOf(other), emptyList()))
    }
}
