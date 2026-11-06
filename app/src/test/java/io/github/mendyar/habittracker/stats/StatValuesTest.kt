package io.github.mendyar.habittracker.stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.ui.Formats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class StatValuesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = TimeZone.getDefault()
    private val math = PeriodMath(zone, Locale.getDefault())

    private fun at(d: Int, h: Int, m: Int = 0) = Calendar.getInstance(zone).apply {
        clear()
        set(2026, 5, d, h, m)
    }.timeInMillis

    /** Ten days (June 1–10): 2 entries a day at 8:00 and 20:00, plus a party day of 12 on June 5. */
    private fun values(config: StatsConfig = StatsConfig(), period: Period = Period.DAY): StatValues {
        val ts = ((1..10).flatMap { d -> listOf(at(d, 8), at(d, 20)) } + (1..10).map { at(5, 21, it) }).sorted().toLongArray()
        val now = at(11, 12)
        val bounds = StatsEngine.resolve(io.github.mendyar.habittracker.core.DateRange.ALL, ts.first(), now, math)
        val buckets = StatsEngine.bucketize(ts, Period.DAY, bounds.first, bounds.last, math)
        return StatValues(context, Formats(context), ts, buckets, bounds, now, math, config, period = period)
    }

    @Test
    fun theTypicalDayIgnoresThePartyDay() {
        val v = values()
        assertEquals("2", v.text(StatKind.MEDIAN))
        assertEquals("2", v.text(StatKind.TRIMMED_MEAN)) // the 12 is trimmed away
        assertEquals("3", v.text(StatKind.MEAN))
        assertEquals("2–12", v.text(StatKind.RANGE))
        assertEquals("0", v.text(StatKind.IQR))
        assertEquals("Middle half of days: 2–2", v.note(StatKind.IQR))
        // Without the flagged party day, an ordinary day has 2.
        assertEquals("2", v.text(StatKind.MEAN_TYPICAL))
        assertEquals("1 flagged as unusually high", v.note(StatKind.MEAN_TYPICAL))
        // Sample standard deviation: sqrt(90 / 9).
        assertEquals("3.16", v.text(StatKind.STD_DEV))
        assertEquals("30", v.text(StatKind.TOTAL))
        assertEquals(10, v.periods)
    }

    @Test
    fun notesAndExplanationsNameThePeriod() {
        val v = values(period = Period.WEEK)
        assertEquals("Pulled up by heavy weeks", v.note(StatKind.MEAN))
        assertEquals("Mean without flagged weeks", v.label(StatKind.MEAN_TYPICAL))
        assertTrue(v.help(StatKind.MEDIAN).startsWith("Your typical week: half of your weeks"))
        assertTrue(v.help(StatKind.TRIMMED_MEAN).contains("10% of weeks"))
        // No explanation mentions "(or weeks…)" any more.
        StatKind.entries.forEach { assertFalse(v.help(it), v.help(it).contains("…")) }
    }

    @Test
    fun theDefaultsAreTheResearchSet() {
        assertEquals(
            listOf(StatKind.MEDIAN, StatKind.TRIMMED_MEAN, StatKind.MEAN, StatKind.MEAN_TYPICAL, StatKind.IQR, StatKind.STD_DEV),
            StatsConfig().statsFor(io.github.mendyar.habittracker.data.HabitType.COUNT),
        )
        assertEquals(
            setOf(StatsCard.HISTORY, StatsCard.HOURS, StatsCard.FIRST_ENTRY, StatsCard.WEEKDAYS, StatsCard.TRIGGERS, StatsCard.ENTRIES),
            StatsConfig().cardsFor(io.github.mendyar.habittracker.data.HabitType.COUNT),
        )
        assertEquals(
            setOf(StatsCard.SESSION_TREND, StatsCard.ENTRIES),
            StatsConfig().cardsFor(io.github.mendyar.habittracker.data.HabitType.DURATION),
        )
    }

    @Test
    fun firstAndLastEntryOfTheDay() {
        val v = values()
        assertEquals(Formats(context).timeOfDay(8 * 60), v.text(StatKind.FIRST_ENTRY))
        assertEquals(Formats(context).timeOfDay(20 * 60), v.text(StatKind.LAST_ENTRY))
    }

    @Test
    fun targetAndEmptyValues() {
        assertEquals(context.getString(R.string.stat_empty), values().text(StatKind.WITHIN_TARGET))
        assertEquals(context.getString(R.string.value_of, "9", "10"), values(StatsConfig(target = 2)).text(StatKind.WITHIN_TARGET))
        assertEquals(context.getString(R.string.value_of, "0", "10"), values().text(StatKind.ZERO_PERIODS))
        assertEquals(context.getString(R.string.stat_empty), values().text(StatKind.CHANGE)) // needs 14 periods
    }

    @Test
    fun configSurvivesStorage() {
        val config = StatsConfig(listOf(StatKind.MEDIAN, StatKind.TOTAL), setOf(StatsCard.WHEN), target = 8, dayStartHour = 4)
        assertEquals(config, StatsConfig.decode(config.encode()))
        // A setup saved by 2.0, which hid cards from showing them all, keeps hiding them.
        val old = StatsConfig.decode("s=*;h=ENTRIES;t=;d=")
        assertFalse(StatsCard.ENTRIES in old.cardsFor(io.github.mendyar.habittracker.data.HabitType.COUNT))
        assertTrue(StatsCard.HOURS in old.cardsFor(io.github.mendyar.habittracker.data.HabitType.COUNT))
        assertEquals(StatsConfig(), StatsConfig.decode(null))
        assertEquals(StatsConfig(), StatsConfig.decode("garbage"))
        assertEquals(true, StatsConfig().isDefault)
    }

    @Test
    fun durationStatisticsDescribeTheSessionLengths() {
        val minutes = longArrayOf(3, 4, 4, 5, 20).map { it * 60_000 }.toLongArray()
        val now = at(11, 12)
        val bounds = at(1, 0)..now
        val v = StatValues(context, Formats(context), LongArray(0), emptyList(), bounds, now, math, StatsConfig(), minutes)
        assertEquals("4:00", v.text(StatKind.DUR_MEDIAN))
        assertEquals("1:00", v.text(StatKind.DUR_IQR))
        assertEquals("Middle half: 4:00–5:00", v.note(StatKind.DUR_IQR))
        assertEquals("3:00–20:00", v.text(StatKind.DUR_RANGE))
        assertEquals("5", v.text(StatKind.SESSIONS))
        // Shorter than the report's example target of 5 minutes: 3 of 5.
        assertEquals(Formats(context).percent(0.6), v.text(StatKind.UNDER_TARGET))
        val withTarget = StatValues(context, Formats(context), LongArray(0), emptyList(), bounds, now, math, StatsConfig(target = 4), minutes)
        assertEquals(Formats(context).percent(0.2), withTarget.text(StatKind.UNDER_TARGET))
        assertEquals(listOf(StatKind.DUR_MEDIAN, StatKind.DUR_IQR, StatKind.DUR_MEAN, StatKind.UNDER_TARGET),
            StatsConfig().statsFor(io.github.mendyar.habittracker.data.HabitType.DURATION))
    }
}
