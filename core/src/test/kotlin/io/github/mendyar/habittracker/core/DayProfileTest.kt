package io.github.mendyar.habittracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DayProfileTest {

    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val math = PeriodMath(zone, Locale.UK)

    private fun at(m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(zone, Locale.UK).apply {
            clear()
            set(2026, m - 1, d, h, min)
        }.timeInMillis

    /** Profile of [timestamps] over October 1–10, seen on October 20. */
    private fun profile(vararg timestamps: Long): DayProfile =
        DayProfile.of(timestamps.sortedArray(), at(10, 1, 0), at(10, 10, 23, 59), at(10, 20, 12), math)

    private fun DayProfile.at(h: Int, min: Int = 0) = values[(h * 60 + min) / stepMinutes]

    @Test
    fun everyDayAtTheSameTimeIsCertainThereAndUnlikelyElsewhere() {
        val p = profile(*LongArray(10) { at(10, it + 1, 8) })
        assertEquals(10, p.days)
        assertEquals(1.0, p.at(8), 1e-9)
        assertEquals(8 * 60, p.minuteAt(p.peakIndex))
        assertEquals(1.0, p.peak, 1e-9)
        assertTrue(p.at(7, 30) in 0.2..0.5) // half a curve width away
        assertEquals(0.0, p.at(20), 1e-9)
        assertEquals(0.0, p.at(10), 1e-6)
    }

    @Test
    fun valuesAreTheShareOfDays() {
        val p = profile(*LongArray(5) { at(10, it * 2 + 1, 8) })
        assertEquals(0.5, p.at(8), 1e-9)
    }

    @Test
    fun aBurstOnOneDayCountsAsOneDay() {
        val burst = LongArray(6) { at(10, 3, 8) + it * 60_000L }
        val p = profile(*burst)
        assertTrue(p.peak <= 0.1 + 1e-9) // one day out of ten
        assertEquals(0.1, p.at(8), 0.001)
    }

    @Test
    fun theProfileNeverExceedsOne() {
        // A heavy habit: an entry every 20 minutes from 7:00 to 23:00, every day.
        val heavy = (1..10).flatMap { d -> (0 until 48).map { at(10, d, 7) + it * 20 * 60_000L } }
        val p = profile(*heavy.toLongArray())
        assertTrue(p.values.all { it in 0.0..1.0 })
        assertEquals(1.0, p.at(15), 1e-9)
        assertTrue(p.at(4) < 0.01)
    }

    @Test
    fun timeOfDayWrapsAroundMidnight() {
        val p = profile(*LongArray(10) { at(10, it + 1, 23, 55) })
        assertTrue(p.at(0, 5) > 0.85)
        assertEquals(p.at(23, 45), p.at(0, 5), 1e-9)
    }

    @Test
    fun aDayStillInProgressIsLeftOut() {
        val now = at(10, 10, 9)
        val ts = longArrayOf(at(10, 8, 8), at(10, 9, 8), at(10, 10, 8))
        val p = DayProfile.of(ts, at(10, 8, 0), now, now, math)
        assertEquals(2, p.days)
        assertTrue(p.excludesToday)
        assertEquals(1.0, p.at(8), 1e-9)

        // Unless it is the only day.
        val first = DayProfile.of(longArrayOf(at(10, 10, 8)), at(10, 10, 8), now, now, math)
        assertEquals(1, first.days)
        assertFalse(first.excludesToday)
        assertEquals(1.0, first.at(8), 1e-9)
    }

    @Test
    fun daysWithoutEntriesCount() {
        val p = profile(at(10, 1, 8))
        assertEquals(10, p.days)
        assertEquals(0.1, p.at(8), 1e-9)
    }

    @Test
    fun nothingLoggedGivesAFlatProfile() {
        val p = profile()
        assertEquals(0.0, p.peak, 0.0)
        assertEquals(24 * 60 / DayProfile.STEP_MINUTES, p.values.size)
    }

    @Test
    fun localClockTimeIsUsedAcrossDaylightSavingChanges() {
        // Clocks go forward on March 29, 2026 in Berlin.
        val ts = LongArray(5) { at(3, 27 + it, 8) }
        val p = DayProfile.of(ts, at(3, 27, 0), at(3, 31, 23), at(4, 5, 0), math)
        assertEquals(5, p.days)
        assertEquals(1.0, p.at(8), 1e-9)
    }
}
