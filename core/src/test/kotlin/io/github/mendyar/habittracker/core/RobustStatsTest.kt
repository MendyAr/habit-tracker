package io.github.mendyar.habittracker.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RobustStatsTest {

    /** Ten ordinary days and one party day. */
    private val days = doubleArrayOf(8.0, 9.0, 7.0, 8.0, 10.0, 9.0, 8.0, 7.0, 9.0, 8.0, 35.0)

    @Test
    fun theMedianIgnoresAPartyDayTheMeanDoesNot() {
        assertEquals(8.0, RobustStats.median(days), 1e-9)
        assertTrue(days.average() > 10.5)
        assertEquals(8.5, RobustStats.median(doubleArrayOf(8.0, 9.0)), 1e-9)
        assertTrue(RobustStats.median(DoubleArray(0)).isNaN())
    }

    @Test
    fun theTrimmedMeanDropsTenPercentAtEachEnd() {
        // 11 values: one dropped at each end (7 and 35).
        val expected = doubleArrayOf(7.0, 8.0, 8.0, 8.0, 8.0, 9.0, 9.0, 9.0, 10.0).average()
        assertEquals(expected, RobustStats.trimmedMean(days), 1e-9)
        assertEquals(5.0, RobustStats.trimmedMean(doubleArrayOf(5.0)), 1e-9)
    }

    @Test
    fun quartilesAndOutliers() {
        val (q1, q3) = RobustStats.quartiles(days)
        assertEquals(8.0, q1, 1e-9)
        assertEquals(9.0, q3, 1e-9)
        val flags = RobustStats.highOutliers(days)
        assertArrayEquals(BooleanArray(10) { false } + true, flags)
        assertTrue(RobustStats.highOutliers(doubleArrayOf(1.0, 50.0)).none { it }) // too few to judge
    }

    @Test
    fun rollingMedianStartsOnceTheWindowIsFull() {
        val r = RobustStats.rollingMedian(days, 7)
        assertTrue(r[5].isNaN())
        assertEquals(8.0, r[6], 1e-9)
        assertEquals(9.0, r[10], 1e-9) // the party day counts as one high value, not as its size
    }

    @Test
    fun changeComparesTheLastPeriodsWithThoseBefore() {
        val values = DoubleArray(7) { 10.0 } + DoubleArray(7) { 8.0 }
        assertEquals(-0.2, RobustStats.change(values)!!, 1e-9)
        assertNull(RobustStats.change(DoubleArray(10) { 1.0 }))
        assertNull(RobustStats.change(DoubleArray(14) { if (it < 7) 0.0 else 1.0 }))
    }

    @Test
    fun medianAbsoluteDeviation() {
        assertEquals(1.0, RobustStats.mad(days), 1e-9)
    }

    @Test
    fun firstAndLastEntryOfTheDay() {
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val math = PeriodMath(zone, Locale.UK)
        fun at(d: Int, h: Int, m: Int = 0) = Calendar.getInstance(zone, Locale.UK).apply {
            clear()
            set(2026, 5, d, h, m)
        }.timeInMillis
        // June 1: a late-night one at 01:00 (not a "first of the day"), then 07:30 and 22:00.
        // June 2: 08:00 only. June 3 is "today" and left out.
        val ts = longArrayOf(at(1, 1), at(1, 7, 30), at(1, 22), at(2, 8), at(3, 6)).sortedArray()
        val now = at(3, 12)
        assertArrayEquals(doubleArrayOf(450.0, 480.0), DailyTimes.firstOfDay(ts, at(1, 0), now, now, math), 1e-9)
        assertArrayEquals(doubleArrayOf(1320.0, 480.0), DailyTimes.lastOfDay(ts, at(1, 0), now, now, math), 1e-9)
        assertArrayEquals(doubleArrayOf(390.0, 870.0, 600.0), DailyTimes.gaps(ts, at(1, 0), at(2, 23)), 1e-9)
    }
}
