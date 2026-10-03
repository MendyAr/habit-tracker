package io.github.mendyar.habittracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PeriodMathTest {

    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val math = PeriodMath(zone, Locale.UK, Calendar.MONDAY)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(zone, Locale.UK).apply {
            clear()
            set(y, m - 1, d, h, min)
        }.timeInMillis

    @Test
    fun dayStartIsLocalMidnight() {
        assertEquals(at(2026, 10, 3), math.startOf(Period.DAY, at(2026, 10, 3, 17, 45)))
    }

    @Test
    fun weekStartsOnConfiguredFirstDay() {
        // Saturday 3 Oct 2026 -> Monday 28 Sep 2026.
        assertEquals(at(2026, 9, 28), math.startOf(Period.WEEK, at(2026, 10, 3, 12)))
        // A Monday is its own week start.
        assertEquals(at(2026, 9, 28), math.startOf(Period.WEEK, at(2026, 9, 28, 0, 1)))
    }

    @Test
    fun weekStartRespectsSundayLocales() {
        val us = PeriodMath(zone, Locale.US, Calendar.SUNDAY)
        assertEquals(at(2026, 9, 27), us.startOf(Period.WEEK, at(2026, 10, 3, 12)))
    }

    @Test
    fun monthAndYearStarts() {
        assertEquals(at(2026, 10, 1), math.startOf(Period.MONTH, at(2026, 10, 31, 23, 59)))
        assertEquals(at(2026, 1, 1), math.startOf(Period.YEAR, at(2026, 7, 14, 9)))
    }

    @Test
    fun shiftCrossesMonthAndYearBoundaries() {
        assertEquals(at(2027, 1, 1), math.shift(Period.DAY, at(2026, 12, 31, 10), 1))
        assertEquals(at(2026, 2, 1), math.shift(Period.MONTH, at(2026, 1, 31), 1))
        assertEquals(at(2025, 12, 1), math.shift(Period.MONTH, at(2026, 1, 15), -1))
        assertEquals(at(2028, 1, 1), math.shift(Period.YEAR, at(2026, 5, 5), 2))
        assertEquals(at(2026, 10, 5), math.shift(Period.WEEK, at(2026, 10, 3), 1))
    }

    @Test
    fun daysAcrossDaylightSavingChangesStillStartAtMidnight() {
        // Clocks go forward on 29 Mar 2026 and back on 25 Oct 2026 in Berlin.
        val springDay = math.startOf(Period.DAY, at(2026, 3, 29, 12))
        assertEquals(23L * 3_600_000, math.endOf(Period.DAY, springDay) - springDay)
        val autumnDay = math.startOf(Period.DAY, at(2026, 10, 25, 12))
        assertEquals(25L * 3_600_000, math.endOf(Period.DAY, autumnDay) - autumnDay)
        assertEquals(at(2026, 10, 26), math.shift(Period.DAY, autumnDay, 1))
    }

    @Test
    fun samePeriod() {
        assertTrue(math.samePeriod(Period.DAY, at(2026, 10, 3, 0, 1), at(2026, 10, 3, 23, 59)))
        assertFalse(math.samePeriod(Period.DAY, at(2026, 10, 3, 23, 59), at(2026, 10, 4, 0, 1)))
        assertTrue(math.samePeriod(Period.WEEK, at(2026, 9, 28), at(2026, 10, 4, 23)))
    }

    @Test
    fun unknownPeriodNameFallsBackToDay() {
        assertEquals(Period.DAY, Period.fromName(null))
        assertEquals(Period.DAY, Period.fromName("FORTNIGHT"))
        assertEquals(Period.YEAR, Period.fromName("YEAR"))
    }
}
