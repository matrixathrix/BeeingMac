package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingRowTest {

    @Test fun old_six_field_row_defaults_modifiedAt_to_id_and_origin_to_phone() {
        val e = decodeRatingRow("1730000000000,7,1729990800000,14th,,💼 Work;Rest")!!
        assertEquals(1730000000000L, e.modifiedAt)
        assertEquals(Origin.PHONE, e.origin)
        assertEquals(listOf("💼 Work", "Rest"), e.tags)
        assertEquals("", e.note)
    }

    @Test fun round_trips_eight_fields() {
        val e = RatingEntry(1730000000000L, 7, 1729990800000L, "14th", "calm", listOf("Rest"),
            modifiedAt = 1730000000005L, origin = Origin.WATCH)
        assertEquals(e, decodeRatingRow(encodeRatingRow(e)))
    }

    @Test fun reclaim_tag_survives_round_trip() {
        val e = RatingEntry(1L, 4, 0L, "1st", tags = listOf(RECLAIM_TAG, "Work"))
        assertEquals(e, decodeRatingRow(encodeRatingRow(e)))
    }

    @Test fun short_row_is_dropped() {
        assertNull(decodeRatingRow("1,2,3"))
    }
}
