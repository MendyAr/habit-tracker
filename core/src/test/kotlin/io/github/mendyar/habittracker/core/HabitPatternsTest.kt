package io.github.mendyar.habittracker.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** [HourShare], [WeekdayCounts] and the research-default additions to [RobustStats]. */
class HabitPatternsTest {

    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val math = PeriodMath(zone, Locale.UK) // weeks start on Monday

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        Calendar.getInstance(zone, Locale.UK).apply {
            clear()
            set(y, m - 1, d, h, min)
        }.timeInMillis

    @Test
    fun hourShareIsTheShareOfAllEntriesInEachHour() {
        val ts = longArrayOf(
            at(2026, 6, 1, 8, 5), at(2026, 6, 1, 8, 50), at(2026, 6, 2, 8, 30),
            at(2026, 6, 2, 12, 0), at(2026, 6, 3, 18, 15), at(2026, 6, 3, 23, 59),
        ).sortedArray()
        val share = HourShare.of(ts, at(2026, 6, 1, 0), at(2026, 6, 4, 0), math)
        assertEquals(6, share.total)
        assertEquals(3, share.counts[8])
        assertEquals(0.5, share.shares[8], 1e-9)
        assertEquals(1, share.counts[23])
        // The three busiest hours; ties go to the earlier hour.
        assertEquals(listOf(8, 12, 18), share.top(3))
        assertEquals(5.0 / 6, share.shareOf(share.top(3)), 1e-9)
    }

    @Test
    fun hourShareOfNothingIsAllZero() {
        val share = HourShare.of(LongArray(0), 0L, 1L, math)
        assertEquals(0, share.total)
        assertTrue(share.shares.all { it == 0.0 })
        assertTrue(share.top().isEmpty())
    }

    @Test
    fun weekdayCountsGiveTheMedianAndMiddleHalfOfEachWeekday() {
        // June 2026: Saturdays 6, 13, 20, 27 with 2, 4, 6 and 20 entries; Mondays 1, 8, 15, 22 with none.
        val ts = ArrayList<Long>()
        listOf(6 to 2, 13 to 4, 20 to 6, 27 to 20).forEach { (day, n) -> repeat(n) { ts += at(2026, 6, day, 10, it) } }
        val w = WeekdayCounts.of(ts.toLongArray().sortedArray(), at(2026, 6, 1, 0), at(2026, 6, 28, 23), at(2026, 7, 1), math)
        assertEquals(Calendar.MONDAY, w.weekdays.first())
        val saturday = w.weekdays.indexOf(Calendar.SATURDAY)
        assertEquals(4, w.samples[saturday])
        assertEquals(5.0, w.medians[saturday], 1e-9) // one heavy Saturday doesn't move it
        assertEquals(3.5, w.q1[saturday], 1e-9)
        assertEquals(9.5, w.q3[saturday], 1e-9)
        assertEquals(0.0, w.medians[w.weekdays.indexOf(Calendar.MONDAY)], 1e-9)
        assertEquals(saturday, w.peakIndex)
        assertEquals(4, w.minSamples)
    }

    @Test
    fun theMeanWithoutFlaggedDaysLeavesOutHeavyDays() {
        val days = doubleArrayOf(5.0, 6.0, 6.0, 7.0, 5.0, 6.0, 25.0)
        assertTrue(RobustStats.highOutliers(days).last())
        assertEquals(35.0 / 6, RobustStats.meanWithoutHighOutliers(days), 1e-9)
        // Q1 = 5.5, Q3 = 6.5: the fence is 8.
        assertEquals(8.0, RobustStats.upperFence(days), 1e-9)
    }

    @Test
    fun theTrailingMedianUsesTheSessionsAvailable() {
        val minutes = doubleArrayOf(8.0, 6.0, 7.0, 3.0, 4.0, 5.0, 2.0, 9.0)
        assertArrayEquals(doubleArrayOf(8.0, 7.0, 7.0, 6.5, 6.0, 5.5, 5.0, 5.0), RobustStats.trailingMedian(minutes, 7), 1e-9)
    }
}
