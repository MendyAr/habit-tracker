package io.github.mendyar.habittracker.stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.ui.Formats
import org.junit.Assert.assertEquals
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
    private fun values(config: StatsConfig = StatsConfig()): StatValues {
        val ts = ((1..10).flatMap { d -> listOf(at(d, 8), at(d, 20)) } + (1..10).map { at(5, 21, it) }).sorted().toLongArray()
        val now = at(11, 12)
        val bounds = StatsEngine.resolve(io.github.mendyar.habittracker.core.DateRange.ALL, ts.first(), now, math)
        val buckets = StatsEngine.bucketize(ts, Period.DAY, bounds.first, bounds.last, math)
        return StatValues(context, Formats(context), ts, buckets, bounds, now, math, config)
    }

    @Test
    fun theTypicalDayIgnoresThePartyDay() {
        val v = values()
        assertEquals("2", v.text(StatKind.MEDIAN))
        assertEquals("2", v.text(StatKind.TRIMMED_MEAN)) // the 12 is trimmed away
        assertEquals("3", v.text(StatKind.MEAN))
        assertEquals("2–12", v.text(StatKind.RANGE))
        assertEquals("2–2", v.text(StatKind.IQR))
        assertEquals("30", v.text(StatKind.TOTAL))
        assertEquals(10, v.periods)
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
        assertEquals(StatsConfig(), StatsConfig.decode(null))
        assertEquals(StatsConfig(), StatsConfig.decode("garbage"))
        assertEquals(true, StatsConfig().isDefault)
    }
}
