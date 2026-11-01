package io.github.mendyar.habittracker.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PositionProfileTest {

    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val math = PeriodMath(zone, Locale.UK) // weeks start on Monday

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance(zone, Locale.UK).apply {
            clear()
            set(y, m - 1, d, h, 0)
        }.timeInMillis

    @Test
    fun weekdaysAreOrderedFromTheLocalesFirstDay() {
        val p = PositionProfile.of(LongArray(0), Period.WEEK, at(2026, 6, 1), at(2026, 6, 28), at(2026, 7, 1), math)
        assertEquals(Calendar.MONDAY, p.positions.first())
        assertEquals(Calendar.SUNDAY, p.positions.last())
        val us = PositionProfile.of(LongArray(0), Period.WEEK, at(2026, 6, 1), at(2026, 6, 28), at(2026, 7, 1), PeriodMath(zone, Locale.US))
        assertEquals(Calendar.SUNDAY, us.positions.first())
    }

    @Test
    fun weekShowsTheShareOfEachWeekdayWithAnEntry() {
        // June 2026: Mondays 1, 8, 15, 22. Saturdays 6, 13, 20, 27.
        val ts = longArrayOf(at(2026, 6, 6), at(2026, 6, 6, 20), at(2026, 6, 13), at(2026, 6, 20), at(2026, 6, 8)).sortedArray()
        val p = PositionProfile.of(ts, Period.WEEK, at(2026, 6, 1), at(2026, 6, 28), at(2026, 7, 1), math)
        val saturday = p.positions.indexOf(Calendar.SATURDAY)
        val monday = p.positions.indexOf(Calendar.MONDAY)
        assertEquals(4, p.samples[saturday])
        assertEquals(0.75, p.values[saturday], 1e-9) // 3 of 4 Saturdays, two entries on one count once
        assertEquals(0.25, p.values[monday], 1e-9)
        assertEquals(saturday, p.peakIndex)
        assertEquals(28, p.units)
    }

    @Test
    fun dayOfMonthDividesByTheMonthsThatHaveThatDay() {
        // January to April 2026: the 31st exists in January and March only.
        val ts = longArrayOf(at(2026, 1, 31), at(2026, 2, 1), at(2026, 3, 1))
        val p = PositionProfile.of(ts, Period.MONTH, at(2026, 1, 1), at(2026, 4, 30), at(2026, 5, 10), math)
        assertEquals(31, p.positions.size)
        assertEquals(2, p.samples[30])
        assertEquals(0.5, p.values[30], 1e-9)
        assertEquals(4, p.samples[0])
        assertEquals(0.5, p.values[0], 1e-9)
        assertEquals(3, p.samples[28]) // the 29th: no February 29 in 2026
    }

    @Test
    fun yearShowsTheShareOfEachMonthWithAnEntry() {
        val ts = longArrayOf(at(2024, 12, 24), at(2025, 12, 31), at(2025, 7, 1)).sortedArray()
        val p = PositionProfile.of(ts, Period.YEAR, at(2024, 1, 1), at(2025, 12, 31), at(2026, 3, 1), math)
        assertEquals(12, p.positions.size)
        assertEquals(1.0, p.values[Calendar.DECEMBER], 1e-9)
        assertEquals(0.5, p.values[Calendar.JULY], 1e-9)
        assertEquals(0.0, p.values[Calendar.MARCH], 1e-9)
        assertArrayEquals(IntArray(12) { 2 }, p.samples)
    }

    @Test
    fun theDayStillRunningIsLeftOut() {
        val now = at(2026, 6, 10, 9)
        val ts = longArrayOf(at(2026, 6, 8), at(2026, 6, 10, 8)) // Monday, and Wednesday today
        val p = PositionProfile.of(ts, Period.WEEK, at(2026, 6, 8, 0), now, now, math)
        assertTrue(p.excludesCurrent)
        assertEquals(0, p.samples[p.positions.indexOf(Calendar.WEDNESDAY)])
        assertEquals(1.0, p.values[p.positions.indexOf(Calendar.MONDAY)], 1e-9)
        assertEquals(0, p.minSamples) // no Wednesday yet
    }
}
