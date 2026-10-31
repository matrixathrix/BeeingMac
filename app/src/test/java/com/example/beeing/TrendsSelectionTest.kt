package com.example.beeing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TrendsSelectionTest {

    private val originalZone: TimeZone = TimeZone.getDefault()

    @Before fun london() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
    @After fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun day(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day) }.timeInMillis

    @Test
    fun aFullWeekIsComparedWithTheWeekBefore() = assertEquals(
        day(2026, Calendar.OCTOBER, 5) to day(2026, Calendar.OCTOBER, 12),
        previousSpan(day(2026, Calendar.OCTOBER, 12), day(2026, Calendar.OCTOBER, 19), monthly = false)
    )

    @Test
    fun aWeekClippedToTheMonthIsComparedWithTheSameNumberOfDaysBefore() = assertEquals(
        day(2026, Calendar.SEPTEMBER, 27) to day(2026, Calendar.OCTOBER, 1),
        previousSpan(day(2026, Calendar.OCTOBER, 1), day(2026, Calendar.OCTOBER, 5), monthly = false)
    )

    @Test
    fun aWeekAcrossTheClockChangeStillStartsAtMidnight() = assertEquals(
        // UK clocks went back on Sun 25 Oct 2026: the week before has 7 days and 1 hour
        day(2026, Calendar.OCTOBER, 19) to day(2026, Calendar.OCTOBER, 26),
        previousSpan(day(2026, Calendar.OCTOBER, 26), day(2026, Calendar.NOVEMBER, 2), monthly = false)
    )

    @Test
    fun aMonthIsComparedWithTheCalendarMonthBefore() = assertEquals(
        day(2026, Calendar.FEBRUARY, 1) to day(2026, Calendar.MARCH, 1),
        previousSpan(day(2026, Calendar.MARCH, 1), day(2026, Calendar.APRIL, 1), monthly = true)
    )

    @Test
    fun januaryIsComparedWithLastDecember() = assertEquals(
        day(2025, Calendar.DECEMBER, 1) to day(2026, Calendar.JANUARY, 1),
        previousSpan(day(2026, Calendar.JANUARY, 1), day(2026, Calendar.FEBRUARY, 1), monthly = true)
    )

    @Test
    fun aWeekReadsAsItsDays() = assertEquals(
        "12–18 Oct",
        spanLabel(day(2026, Calendar.OCTOBER, 12), day(2026, Calendar.OCTOBER, 19), monthly = false, locale = Locale.US)
    )

    @Test
    fun aOneDaySliverReadsAsThatDay() = assertEquals(
        "31 Oct",
        spanLabel(day(2026, Calendar.OCTOBER, 31), day(2026, Calendar.NOVEMBER, 1), monthly = false, locale = Locale.US)
    )

    @Test
    fun aWeekAcrossMonthsNamesBoth() = assertEquals(
        "28 Sep – 4 Oct",
        spanLabel(day(2026, Calendar.SEPTEMBER, 28), day(2026, Calendar.OCTOBER, 5), monthly = false, locale = Locale.US)
    )

    @Test
    fun aMonthReadsAsMonthAndYear() = assertEquals(
        "March 2026",
        spanLabel(day(2026, Calendar.MARCH, 1), day(2026, Calendar.APRIL, 1), monthly = true, locale = Locale.US)
    )
}
