package com.example.beeing.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class RatingFlowTest {

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, 10, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun starts_on_the_expiring_hour() {
        val f = startFlow(at(15, 20), emptySet())!!
        assertEquals(at(13), f.hour)
        assertEquals(listOf(at(13), at(14)), f.waiting)
    }

    @Test fun starts_on_the_asked_hour_when_it_waits() {
        assertEquals(at(14), startFlow(at(15, 20), emptySet(), preferHour = at(14))!!.hour)
    }

    @Test fun nothing_waiting_starts_nothing() {
        assertNull(startFlow(at(15, 20), setOf(at(13), at(14))))
    }

    @Test fun expired_hour_hands_choices_to_newer_hour() {
        val f = RatingFlow(at(13), listOf(at(13), at(14)), 7, setOf("Work"), RatingFlow.Step.TAGS)
        val next = f.onClock(at(16, 0), emptySet())!!
        assertEquals(at(15), next.hour)
        assertEquals(7, next.score)
        assertEquals(setOf("Work"), next.tags)
        assertEquals(RatingFlow.Step.TAGS, next.step)
        assertEquals("That hour just closed", next.notice)
    }

    @Test fun expired_with_none_waiting_closes() {
        val f = RatingFlow(at(13), listOf(at(13)), 7, setOf("Work"), RatingFlow.Step.TAGS)
        assertNull(f.onClock(at(16, 0), setOf(at(14), at(15))))
    }

    @Test fun clock_tick_inside_the_hour_changes_nothing() {
        val f = RatingFlow(at(13), listOf(at(13), at(14)), 7, emptySet(), RatingFlow.Step.SCORE)
        assertEquals(f, f.onClock(at(15, 40), emptySet()))
    }

    @Test fun after_save_moves_to_other_waiting_hour() {
        val f = RatingFlow(at(13), listOf(at(13), at(14)), 7, setOf("Work"), RatingFlow.Step.TAGS)
        assertEquals(
            RatingFlow(at(14), listOf(at(14)), null, emptySet(), RatingFlow.Step.SCORE),
            f.afterSave(at(15, 20), setOf(at(13)))
        )
    }

    @Test fun after_last_save_closes() {
        val f = RatingFlow(at(14), listOf(at(14)), 7, setOf("Work"), RatingFlow.Step.TAGS)
        assertNull(f.afterSave(at(15, 20), setOf(at(13), at(14))))
    }

    @Test fun switch_hour_toggles_between_two() {
        val f = RatingFlow(at(13), listOf(at(13), at(14)), 7, setOf("Work"), RatingFlow.Step.SCORE)
        val g = f.switchHour()
        assertEquals(at(14), g.hour)
        assertNull(g.score)
        assertEquals(at(13), g.switchHour().hour)
        val single = RatingFlow(at(14), listOf(at(14)), 7, emptySet(), RatingFlow.Step.SCORE)
        assertEquals(single, single.switchHour())
    }
}
