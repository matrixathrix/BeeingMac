package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationDraftTest {
    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun entry(ts: Long, vararg tags: String) =
        RatingEntry(ts, 7, ts, "", "", tags.toList())

    private val all = listOf("Work", "Food", "Family", "Rest", "Social", "Learning", "Exercise", "Reading")

    @Test fun quick_tags_rank_by_recent_use_then_list_order() {
        val now = at(29, 12)
        val r = listOf(
            entry(at(28, 9), "Rest"), entry(at(28, 10), "Rest"), entry(at(28, 11), "Rest", "Food"),
            entry(at(27, 9), "Food")
        )
        assertEquals(listOf("Rest", "Food", "Work", "Family", "Social", "Learning"), quickTags(r, all, now, 6))
    }

    @Test fun quick_tags_ignore_old_entries_the_sentinel_and_deleted_tags() {
        val now = at(29, 12)
        val old = entry(at(29, 12) - 40L * 24 * 3_600_000L, "Reading", "Reading")
        val r = listOf(old, entry(at(28, 9), RECLAIM_TAG, "Ghost", "Learning"))
        val q = quickTags(r, all, now, 6)
        assertEquals("Learning", q.first())
        assertFalse("Ghost" in q)
        assertFalse(RECLAIM_TAG in q)
        assertEquals(6, q.size)
    }

    @Test fun quick_tags_with_no_history_is_the_first_of_the_list() {
        assertEquals(all.take(6), quickTags(emptyList(), all, at(29, 12), 6))
        assertEquals(listOf("Work"), quickTags(emptyList(), listOf("Work"), at(29, 12), 6))
    }

    @Test fun submit_needs_a_score_and_at_least_one_tag() {
        assertFalse(NotifDraft(1L, null, listOf("Work")).canSubmit)
        assertFalse(NotifDraft(1L, 7, emptyList()).canSubmit)
        assertTrue(NotifDraft(1L, 7, listOf("Work")).canSubmit)
    }

    @Test fun toggling_a_tag_adds_then_removes_it() {
        val d = NotifDraft(1L, 7, emptyList()).toggleTag("Work").toggleTag("Food")
        assertEquals(listOf("Work", "Food"), d.tags)
        assertEquals(listOf("Food"), d.toggleTag("Work").tags)
    }

    @Test fun a_draft_for_another_hour_starts_fresh() {
        val stored = NotifDraft(100L, 7, listOf("Work"))
        assertEquals(stored, draftFor(stored, 100L))
        assertEquals(NotifDraft(200L, null, emptyList()), draftFor(stored, 200L))
        assertEquals(NotifDraft(200L, null, emptyList()), draftFor(null, 200L))
    }

    @Test fun draft_round_trips_through_storage() {
        val d = NotifDraft(1234L, 9, listOf("Work", "Social"))
        assertEquals(d, parseDraft(d.serialize()))
        val noScore = NotifDraft(1234L, null, emptyList())
        assertEquals(noScore, parseDraft(noScore.serialize()))
        assertNull(parseDraft(""))
        assertNull(parseDraft("garbage"))
    }

    @Test fun only_the_last_two_completed_hours_are_still_ratable() {
        val now = at(29, 14, 20)
        assertTrue(hourStillRatable(at(29, 13), now))
        assertTrue(hourStillRatable(at(29, 12), now))
        assertFalse(hourStillRatable(at(29, 11), now))
        assertFalse(hourStillRatable(at(29, 14), now))
    }

    @Test fun hour_label_is_the_ordinal_of_the_hour_end() {
        assertEquals("14th", hourLabelFor(at(29, 13)))
        assertEquals("24th", hourLabelFor(at(29, 23)))
        assertEquals("1st", hourLabelFor(at(29, 0)))
        assertEquals("22nd", hourLabelFor(at(29, 21)))
    }
}
