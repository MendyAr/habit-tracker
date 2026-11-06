package io.github.mendyar.habittracker.core

import java.util.Calendar

/**
 * Daily counts grouped by weekday: the median of each weekday with its middle half
 * (Q1 to Q3), so one heavy Saturday doesn't make every Saturday look heavy.
 *
 * @property weekdays [Calendar.DAY_OF_WEEK] values from the locale's first day of the week.
 * @property medians median count per weekday (NaN for a weekday without days).
 * @property q1 first quartile per weekday.
 * @property q3 third quartile per weekday.
 * @property samples how many days of each weekday the window holds.
 */
class WeekdayCounts(
    val weekdays: IntArray,
    val medians: DoubleArray,
    val q1: DoubleArray,
    val q3: DoubleArray,
    val samples: IntArray,
) {
    /** The fewest days of any weekday: below a handful of weeks the medians are rough. */
    val minSamples: Int get() = samples.minOrNull() ?: 0

    /** Index of the highest median (the first one on ties), or -1 without data. */
    val peakIndex: Int get() = medians.indices.filter { !medians[it].isNaN() }.maxByOrNull { medians[it] } ?: -1

    companion object {
        /**
         * From [sortedTimestamps] over `[from, to]`: every finished day counts, days
         * without entries as 0. The day still running at [now] is left out unless it is
         * the only one.
         */
        fun of(sortedTimestamps: LongArray, from: Long, to: Long, now: Long, math: PeriodMath): WeekdayCounts {
            val weekdays = IntArray(7) { (math.firstDayOfWeek - 1 + it) % 7 + 1 }
            val all = StatsEngine.bucketize(sortedTimestamps, Period.DAY, from, to, math)
            val days = all.filter { it.end <= now }.ifEmpty { all }
            val perWeekday = Array(7) { ArrayList<Double>() }
            for (day in days) perWeekday[weekdays.indexOf(math.field(day.start, Calendar.DAY_OF_WEEK))].add(day.count.toDouble())
            val values = perWeekday.map { it.toDoubleArray() }
            return WeekdayCounts(
                weekdays,
                DoubleArray(7) { RobustStats.median(values[it]) },
                DoubleArray(7) { RobustStats.quantile(values[it], 0.25) },
                DoubleArray(7) { RobustStats.quantile(values[it], 0.75) },
                IntArray(7) { values[it].size },
            )
        }
    }
}
