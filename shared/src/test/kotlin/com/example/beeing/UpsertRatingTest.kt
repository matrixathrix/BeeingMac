package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Test

/** One row per hour: a save for an hour the phone already holds replaces it. */
class UpsertRatingTest {
    private val hour = 1_793_000_000_000L - (1_793_000_000_000L % 3_600_000L)
    private val other = RatingEntry(1L, 5, hour - 3_600_000L, "")

    @Test fun edit_replaces_by_id() {
        val e = RatingEntry(2L, 6, hour, "")
        assertEquals(listOf(other, e.copy(score = 9)), upsertRating(listOf(other, e), e.copy(score = 9)))
    }

    @Test fun a_second_save_for_the_same_hour_replaces_the_first() {
        // The watch's rating merged while the Now tab still showed the hour as waiting.
        val fromWatch = RatingEntry(3L, 6, hour, "", origin = Origin.WATCH)
        val fromPhone = RatingEntry(4L, 8, hour, "")
        assertEquals(listOf(other, fromPhone), upsertRating(listOf(other, fromWatch), fromPhone))
    }
}
