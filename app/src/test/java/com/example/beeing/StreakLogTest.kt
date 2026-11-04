package com.example.beeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** The History log and bell readers over streak events (they live in :app). */
class StreakLogTest {

    private fun dayStart(offset: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 28, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -offset)
    }.timeInMillis

    // --- Task 8: the record ---------------------------------------------------

    private fun logEv(type: StreakEventType, dayMs: Long, id: String, savers: Int = 0) =
        StreakEvent(id, dayMs + 1000L, type, 0, savers, 0, dayMs)

    @Test fun log_rows_merge_saver_into_super_day() {
        val d = dayStart(1)
        val rows = logRows(
            listOf(
                logEv(StreakEventType.SUPER_DAY, d, "SUPER_DAY:a"),
                logEv(StreakEventType.SAVER_EARNED, d, "SAVER_EARNED:a"),
                logEv(StreakEventType.COMPLETED, d, "COMPLETED:a")
            )
        )
        assertEquals(
            listOf(StreakEventType.SUPER_DAY to true, StreakEventType.COMPLETED to false),
            rows.map { it.first.type to it.second }
        )
    }

    @Test fun super_day_log_detail_at_cap() {
        val oct3 = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 3, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val e = logEv(StreakEventType.SUPER_DAY, oct3, "SUPER_DAY:x", savers = 4)
        assertEquals("12 hours on 3 Oct — all 5 savers saved up", e.logDetail(false))
        assertEquals("12 hours on 3 Oct — streak saver earned, 4 of 5 saved up", e.logDetail(true))
    }

    @Test fun super_day_is_not_a_warning_or_alert() {
        val e = logEv(StreakEventType.SUPER_DAY, dayStart(1), "SUPER_DAY:y", savers = 3)
        assertTrue(warningsFrom(listOf(e), 0L, e.timestamp) { "" }.isEmpty())
        assertTrue(alertsToPost(listOf(e), emptySet(), 0L).isEmpty())
    }
}
