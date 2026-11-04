package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireTest {

    private val rating = RatingEntry(1_793_000_400_000L, 8, 1_793_000_000_000L, "14th", "",
        listOf("💼 Work", "🏃 Exercise"), modifiedAt = 1_793_000_500_000L, origin = Origin.WATCH)

    private val state = PhoneState(
        syncedAtMs = 1_793_000_600_000L,
        tags = listOf("💼 Work", "Rest", "🏃 Exercise"),
        topTags = listOf("💼 Work", "Rest"),
        modeEvents = listOf(ModeEvent(1_792_000_000_000L, true)),
        prefs = NotificationPrefs(true, 22 * 60, 7 * 60, 1_793_100_000_000L, PauseMode.QUIET),
        carry = ReplayCarry(1_792_900_000_000L, 4, 3, 0, 1, 6, 1, 1, 2, true),
        recentRatings = listOf(rating, rating.copy(id = 5L, tags = listOf(RECLAIM_TAG), origin = Origin.PHONE)),
        mergedWatchIds = listOf(rating.id)
    )

    @Test fun state_round_trips() = assertEquals(state, decodeState(encodeState(state)))

    @Test fun rating_round_trips_with_emoji_tags() = assertEquals(rating, decodeRating(encodeRating(rating)))

    @Test fun unknown_fields_are_ignored() {
        val json = String(encodeRating(rating), Charsets.UTF_8).dropLast(1) + ""","fromTheFuture":42}"""
        assertEquals(rating, decodeRating(json.toByteArray(Charsets.UTF_8)))
    }

    @Test fun garbage_decodes_to_null() {
        assertNull(decodeState("not json".toByteArray()))
        assertNull(decodeRating(byteArrayOf(0, 1, 2)))
    }
}
