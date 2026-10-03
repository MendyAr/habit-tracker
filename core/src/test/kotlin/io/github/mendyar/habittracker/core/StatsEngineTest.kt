package io.github.mendyar.habittracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class StatsEngineTest {

    private val zone = TimeZone.getTimeZone("America/New_York")
    private val math = PeriodMath(zone, Locale.US, Calendar.SUNDAY)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(y, m - 1, d, h, min)
        }.timeInMillis

    @Test
    fun bucketizeIncludesEmptyPeriods() {
        val ts = longArrayOf(at(2026, 10, 1, 8), at(2026, 10, 1, 9), at(2026, 10, 3, 22))
        val buckets = StatsEngine.bucketize(ts, Period.DAY, ts.first(), at(2026, 10, 3, 23), math)
        assertEquals(listOf(2, 0, 1), buckets.map { it.count })
        assertEquals(at(2026, 10, 1), buckets.first().start)
        assertEquals(at(2026, 10, 4), buckets.last().end)
    }

    @Test
    fun bucketizeIgnoresTimestampsOutsideRange() {
        val ts = longArrayOf(at(2026, 9, 1), at(2026, 10, 2, 5), at(2026, 10, 9))
        val buckets = StatsEngine.bucketize(ts, Period.DAY, at(2026, 10, 2), at(2026, 10, 3, 12), math)
        assertEquals(listOf(1, 0), buckets.map { it.count })
    }

    @Test
    fun bucketizeByWeekMonthAndYear() {
        val ts = longArrayOf(
            at(2025, 12, 31, 23), // Wed, week of Sun 28 Dec 2025
            at(2026, 1, 1, 1), // same week, next year
            at(2026, 1, 4, 10), // Sun, new week
            at(2026, 2, 14, 12),
        )
        val now = at(2026, 2, 20)
        assertEquals(
            listOf(2, 1, 0, 0, 0, 0, 1, 0),
            StatsEngine.bucketize(ts, Period.WEEK, ts.first(), now, math).map { it.count },
        )
        assertEquals(listOf(1, 2, 1), StatsEngine.bucketize(ts, Period.MONTH, ts.first(), now, math).map { it.count })
        assertEquals(listOf(1, 3), StatsEngine.bucketize(ts, Period.YEAR, ts.first(), now, math).map { it.count })
    }

    @Test
    fun summaryStatistics() {
        val buckets = listOf(2, 4, 4, 4, 5, 5, 7, 9).mapIndexed { i, c -> Bucket(i * 10L, i * 10L + 10, c) }
        val s = StatsEngine.summarize(buckets, now = 1_000)!!
        assertEquals(8, s.periods)
        assertEquals(40, s.total)
        assertEquals(5.0, s.average, 1e-9)
        assertEquals(2, s.min)
        assertEquals(9, s.max)
        assertEquals(4.0, s.variance, 1e-9)
        assertEquals(2.0, s.stdDev, 1e-9)
        assertFalse(s.excludesCurrent)
    }

    @Test
    fun summaryExcludesRunningPeriodButCountsItInTotal() {
        val buckets = listOf(Bucket(0, 10, 4), Bucket(10, 20, 6), Bucket(20, 30, 1))
        val s = StatsEngine.summarize(buckets, now = 25)!!
        assertEquals(2, s.periods)
        assertEquals(5.0, s.average, 1e-9)
        assertEquals(4, s.min)
        assertEquals(11, s.total)
        assertTrue(s.excludesCurrent)
    }

    @Test
    fun summaryUsesRunningPeriodWhenItIsTheOnlyOne() {
        val s = StatsEngine.summarize(listOf(Bucket(0, 10, 3)), now = 5)!!
        assertEquals(1, s.periods)
        assertEquals(3.0, s.average, 1e-9)
        assertEquals(0.0, s.variance, 1e-9)
        assertFalse(s.excludesCurrent)
    }

    @Test
    fun summaryOfNothingIsNull() {
        assertNull(StatsEngine.summarize(emptyList(), 0))
    }

    @Test
    fun resolveOpenRangeUsesFirstTimestampAndNow() {
        val now = at(2026, 10, 3, 15)
        assertEquals(at(2026, 9, 1, 7)..now, StatsEngine.resolve(DateRange.ALL, at(2026, 9, 1, 7), now, math))
        assertEquals(now..now, StatsEngine.resolve(DateRange.ALL, null, now, math))
    }

    @Test
    fun resolveCustomRangeIsInclusiveAndClampedToNow() {
        val now = at(2026, 10, 3, 15)
        val range = DateRange(fromDay = at(2026, 9, 10, 18), toDay = at(2026, 9, 20, 3))
        assertEquals(at(2026, 9, 10)..(at(2026, 9, 21) - 1), StatsEngine.resolve(range, null, now, math))
        val future = DateRange(fromDay = at(2026, 10, 1), toDay = at(2026, 12, 1))
        assertEquals(at(2026, 10, 1)..now, StatsEngine.resolve(future, null, now, math))
    }

    @Test
    fun resolveSwapsReversedRange() {
        val now = at(2026, 10, 3, 15)
        val range = DateRange(fromDay = at(2026, 9, 20), toDay = at(2026, 9, 10))
        val resolved = StatsEngine.resolve(range, null, now, math)
        assertTrue(resolved.first < resolved.last)
    }

    @Test
    fun countBetween() {
        val ts = longArrayOf(1, 5, 5, 9, 12)
        assertEquals(3, StatsEngine.countBetween(ts, 5, 10))
        assertEquals(0, StatsEngine.countBetween(ts, 13, 20))
        assertEquals(5, StatsEngine.countBetween(ts, 0, 13))
    }

    @Test
    fun niceCeiling() {
        assertEquals(1, StatsEngine.niceCeiling(0))
        assertEquals(2, StatsEngine.niceCeiling(2))
        assertEquals(5, StatsEngine.niceCeiling(3))
        assertEquals(10, StatsEngine.niceCeiling(7))
        assertEquals(20, StatsEngine.niceCeiling(11))
        assertEquals(500, StatsEngine.niceCeiling(201))
    }
}
