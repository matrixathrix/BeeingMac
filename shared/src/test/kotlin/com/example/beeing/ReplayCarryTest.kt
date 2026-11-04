package com.example.beeing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Stopping the replay at any day and resuming it must give exactly the full replay. */
class ReplayCarryTest {

    private val defaultZone: TimeZone = TimeZone.getDefault()
    @After fun restoreZone() = TimeZone.setDefault(defaultZone)

    private var nowMs = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 28, 20, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayStart(offset: Int): Long = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -offset)
    }.timeInMillis

    private var nextId = 1L
    private fun entry(offset: Int, hour: Int) = RatingEntry(
        id = nextId++, score = 7,
        timestamp = Calendar.getInstance().apply { timeInMillis = dayStart(offset); set(Calendar.HOUR_OF_DAY, hour) }.timeInMillis,
        hourLabel = "", tags = listOf("Work")
    )

    private fun day(offset: Int, n: Int) = (0 until n).map { entry(offset, it) }

    /** Consecutive days ending yesterday, oldest first, plus hours today. */
    private fun hist(counts: List<Int>, today: Int = 0): List<RatingEntry> =
        counts.flatMapIndexed { i, n -> day(counts.size - i, n) } + day(0, today)

    private fun beginnerOn(vararg offsets: Int): List<ModeEvent> =
        offsets.flatMap { listOf(ModeEvent(dayStart(it), true), ModeEvent(dayStart(it - 1), false)) }
            .sortedBy { it.timestamp }

    private fun StreakState.key() = listOf(
        currentStreak, displayStreak, savers, recoveryOpen, recoveryDaysLeft,
        beginnerDaysCompleted, todayHours, todayEarnedSaver, todayRecovered, recentDays
    )

    /** Every checkpoint from the first day to the day before yesterday resumes to the full state. */
    private fun assertEverySplit(r: List<RatingEntry>, m: List<ModeEvent> = emptyList()) {
        val full = computeStreakState(r, m, nowMs).key()
        val first = r.minOf { it.timestamp }
        var offset = 2
        while (dayStart(offset) >= Calendar.getInstance().apply {
                timeInMillis = first
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis) {
            val carry = carryAt(r, m, dayStart(offset))
            assertEquals("checkpoint at day -$offset", full, computeStreakStateFrom(carry, r, m, nowMs).key())
            offset++
        }
    }

    @Test fun ten_complete_days() = assertEverySplit(hist(List(10) { 8 }, today = 3))

    @Test fun break_then_recovery_on_window_day_two() =
        assertEverySplit(hist(listOf(8, 8, 8, 0, 0, 0, 2, 10), today = 1))

    @Test fun window_closes_unrecovered_with_two_qualifying_days() =
        assertEverySplit(hist(listOf(8, 8, 8, 0, 0, 0, 8, 2, 8, 1, 1)))

    @Test fun eight_idle_days_bring_the_comeback_gift() =
        assertEverySplit(hist(listOf(8, 0, 0, 0, 0, 0, 0, 0, 0, 3)))

    @Test fun savers_spent_down_then_broken() =
        assertEverySplit(hist(listOf(8, 8, 0, 0, 0, 1, 1)))

    @Test fun beginner_stretch_across_a_running_streak() =
        assertEverySplit(hist(listOf(8, 8, 4, 4, 2, 8, 8), today = 5), beginnerOn(5, 4, 3))

    @Test fun super_day_at_the_saver_cap() =
        assertEverySplit(hist(listOf(12, 12, 12, 12, 12, 8, 12), today = 12))

    @Test fun resume_starts_at_local_midnight_across_dst() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        nowMs = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 28, 20, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // 25 Oct 2026 is the 25-hour day in London (offset 3 from 28 Oct).
        val r = hist(listOf(8, 8, 8, 0, 8, 8, 8), today = 2)
        val full = computeStreakState(r, emptyList(), nowMs).key()
        assertEquals(full, computeStreakStateFrom(carryAt(r, emptyList(), dayStart(3)), r, emptyList(), nowMs).key())
        assertEquals(full, computeStreakStateFrom(carryAt(r, emptyList(), dayStart(4)), r, emptyList(), nowMs).key())
    }
}
