package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StreakRulesTest {

    private fun now(): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 28, 20, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayStart(offset: Int): Long = Calendar.getInstance().apply {
        timeInMillis = now()
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        add(Calendar.DAY_OF_YEAR, -offset)
    }.timeInMillis

    private fun keyOf(offset: Int): Long = Calendar.getInstance().apply { timeInMillis = dayStart(offset) }.dayKey()

    private var nextId = 1L
    private fun entry(offset: Int, hour: Int, backdated: Boolean = false) = RatingEntry(
        id = nextId++, score = 7,
        timestamp = Calendar.getInstance().apply {
            timeInMillis = dayStart(offset); set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis,
        hourLabel = "", tags = if (backdated) listOf("Work", RECLAIM_TAG) else listOf("Work")
    )

    /** `organic` in-the-moment hours 0.., then `backdated` more hours after them. */
    private fun day(offset: Int, organic: Int, backdated: Int = 0): List<RatingEntry> =
        (0 until organic).map { entry(offset, it) } +
            (organic until organic + backdated).map { entry(offset, it, backdated = true) }

    /** Consecutive days ending yesterday, oldest first, plus optional organic hours today. */
    private fun hist(counts: List<Int>, today: Int = 0): List<RatingEntry> =
        counts.flatMapIndexed { i, n -> day(counts.size - i, n) } + day(0, today)

    private fun beginnerOn(vararg offsets: Int): List<ModeEvent> =
        offsets.flatMap { listOf(ModeEvent(dayStart(it), true), ModeEvent(dayStart(it - 1), false)) }
            .sortedBy { it.timestamp }

    private fun state(r: List<RatingEntry>, m: List<ModeEvent> = emptyList()) = computeStreakState(r, m, now())
    private fun events(r: List<RatingEntry>, m: List<ModeEvent> = emptyList()) =
        computeStreakEvents(r, emptyList(), m, now())
    private fun outcomes(r: List<RatingEntry>, m: List<ModeEvent> = emptyList()) = computeDayOutcomes(r, m, now())
    private fun List<StreakEvent>.countOf(t: StreakEventType) = count { it.type == t }

    // 3 qualifying days, then two saver spends, then the break
    private val brokenThree = listOf(8, 8, 8, 0, 0, 0)

    @Test fun starts_with_two_savers() {
        val s = state(emptyList())
        assertEquals(2, s.savers); assertEquals(0, s.currentStreak)
    }

    @Test fun twelve_organic_hours_earns_a_saver() {
        val r = hist(listOf(12))
        assertEquals(3, state(r).savers)
        assertEquals(1, events(r).countOf(StreakEventType.SAVER_EARNED))
    }

    @Test fun backdated_hours_do_not_count() {
        assertEquals(2, state(day(1, 8, 4)).savers)
        assertNotEquals(DayOutcome.QUALIFIED, outcomes(day(1, 6, 2))[keyOf(1)])
        assertEquals(3, state(day(0, 3, 2)).todayHours)
    }

    @Test fun same_hour_backdated_and_organic_counts_once_as_organic() {
        val qualifies = day(2, 8) + entry(2, 7, backdated = true)
        assertEquals(DayOutcome.QUALIFIED, outcomes(qualifies)[keyOf(2)])
        val short = day(1, 7) + entry(1, 7, backdated = true) + entry(1, 0, backdated = true)
        assertNotEquals(DayOutcome.QUALIFIED, outcomes(short)[keyOf(1)])
    }

    @Test fun savers_cap_at_five() {
        val r = hist(listOf(12, 12, 12, 12))
        assertEquals(5, state(r).savers)
        assertEquals(3, events(r).countOf(StreakEventType.SAVER_EARNED))
    }

    @Test fun miss_with_streak_uses_saver() {
        val r = hist(listOf(8, 8, 8, 0))
        val s = state(r)
        assertEquals(DayOutcome.SAVED, outcomes(r)[keyOf(1)])
        assertEquals(3, s.currentStreak); assertEquals(1, s.savers)
        val e = events(r)
        assertEquals(1, e.countOf(StreakEventType.SAVER_USED))
        assertEquals(1, e.countOf(StreakEventType.LOW_SAVERS))
    }

    @Test fun miss_at_streak_zero_fades_saver() {
        val r = hist(listOf(1))
        assertEquals(DayOutcome.FADED, outcomes(r)[keyOf(1)])
        assertEquals(1, state(r).savers)
        assertEquals(1, events(r).countOf(StreakEventType.SAVER_FADED))
    }

    @Test fun miss_with_no_savers_breaks_and_opens_window() {
        val r = hist(brokenThree)
        val s = state(r)
        assertEquals(DayOutcome.BROKEN, outcomes(r)[keyOf(1)])
        assertTrue(s.recoveryOpen); assertEquals(3, s.displayStreak); assertEquals(0, s.currentStreak)
        assertEquals(3, s.recoveryDaysLeft)
        assertEquals(1, events(r).countOf(StreakEventType.STREAK_BROKE))
    }

    @Test fun ten_hours_on_window_day_1_2_3_recovers() {
        val cases = listOf(listOf(10), listOf(3, 10), listOf(3, 3, 10))
        for (window in cases) {
            val r = hist(brokenThree + window)
            val s = state(r)
            assertEquals(DayOutcome.RECOVERED, outcomes(r)[keyOf(1)])
            assertFalse(s.recoveryOpen)
            assertEquals(4, s.currentStreak)
        }
    }

    @Test fun recovery_counts_qualifying_days_and_ignores_short_ones() {
        assertEquals(5, state(hist(brokenThree + listOf(8, 3, 10))).currentStreak)
    }

    @Test fun day_four_does_not_recover() {
        val r = hist(brokenThree + listOf(3, 3, 3, 10))
        assertEquals(DayOutcome.QUALIFIED, outcomes(r)[keyOf(1)])
        assertEquals(1, state(r).currentStreak)
    }

    @Test fun closed_window_keeps_trailing_run() {
        assertEquals(1, state(hist(brokenThree + listOf(8, 3, 8))).currentStreak)
        assertEquals(0, state(hist(brokenThree + listOf(8, 8, 3))).currentStreak)
    }

    @Test fun no_burn_inside_open_window() {
        val r = hist(brokenThree + listOf(1))
        val e = events(r)
        assertEquals(2, e.countOf(StreakEventType.SAVER_USED))
        assertEquals(0, e.countOf(StreakEventType.SAVER_FADED))
        assertEquals(0, state(r).savers)
    }

    @Test fun gifted_savers_survive_an_open_window() {
        // 5 savers, five saved misses, the break; window day 1 is idle day 7 => gift
        val r = hist(listOf(12, 12, 12) + List(8) { 0 })
        val s = state(r)
        assertTrue(s.recoveryOpen)
        assertEquals(2, s.savers)
        assertEquals(1, events(r).countOf(StreakEventType.COMEBACK_GIFT))
    }

    @Test fun today_ten_hours_recovers_immediately() {
        val r = hist(brokenThree, today = 10)
        val s = state(r)
        assertEquals(DayOutcome.RECOVERED, outcomes(r)[keyOf(0)])
        assertFalse(s.recoveryOpen); assertEquals(4, s.currentStreak)
    }

    @Test fun recovery_days_left_events_at_two_and_one() {
        val e = events(hist(brokenThree + listOf(3, 3, 3))).filter { it.type == StreakEventType.RECOVERY_DAYS_LEFT }
        assertEquals(listOf(1, 2), e.map { it.daysLeft }.sorted())
        assertEquals(2, e.map { it.id }.toSet().size)
    }

    @Test fun beginner_days_use_window_days_and_cannot_recover() {
        val r = hist(brokenThree + listOf(10))
        val m = beginnerOn(1)
        val s = state(r, m)
        assertEquals(DayOutcome.BEGINNER_COMPLETE, outcomes(r, m)[keyOf(1)])
        assertTrue(s.recoveryOpen); assertEquals(2, s.recoveryDaysLeft)
        assertEquals(0, state(hist(brokenThree + listOf(10, 3, 3)), beginnerOn(3)).currentStreak)
    }

    @Test fun beginner_days_neither_earn_nor_spend() {
        assertEquals(2, state(hist(listOf(12)), beginnerOn(1)).savers)
        assertEquals(2, state(hist(listOf(1)), beginnerOn(1)).savers)
    }

    @Test fun comeback_gift_after_seven_idle_days() {
        val idle = { n: Int -> hist(listOf(1) + List(n - 1) { 0 }) }
        assertEquals(2, state(idle(7)).savers)
        assertEquals(1, events(idle(7)).countOf(StreakEventType.COMEBACK_GIFT))
        assertEquals(DayOutcome.FADED, outcomes(idle(8))[keyOf(1)]); assertEquals(1, state(idle(8)).savers)
        assertEquals(DayOutcome.FADED, outcomes(idle(9))[keyOf(1)]); assertEquals(0, state(idle(9)).savers)
        assertEquals(2, events(idle(14)).countOf(StreakEventType.COMEBACK_GIFT))
    }

    @Test fun beginner_completion_resets_idle_count() {
        val r = hist(listOf(1, 0, 0, 0, 0, 4, 0))
        assertEquals(0, events(r, beginnerOn(2)).countOf(StreakEventType.COMEBACK_GIFT))
    }

    @Test fun today_in_progress_has_no_verdict() {
        val r = hist(listOf(8, 8), today = 3)
        assertEquals(null, outcomes(r)[keyOf(0)])
        assertEquals(2, state(r).currentStreak)
    }

    @Test fun window_across_dst_change() {
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        try {
            // break on 24 Oct, 25 Oct is a 25-hour day, recovery on 27 Oct
            val r = hist(listOf(8, 8, 8, 0, 0, 0, 3, 3, 10))
            val s = state(r)
            assertEquals(DayOutcome.BROKEN, outcomes(r)[keyOf(4)])
            assertEquals(DayOutcome.RECOVERED, outcomes(r)[keyOf(1)])
            assertEquals(4, s.currentStreak); assertFalse(s.recoveryOpen)
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
