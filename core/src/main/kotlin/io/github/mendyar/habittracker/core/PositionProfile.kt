package io.github.mendyar.habittracker.core

import java.util.Calendar

/**
 * How likely a habit is at each position within a week, month or year: the
 * share of Mondays (or of 15ths of the month, or of Marches) in the window that
 * had at least one entry. It is the counterpart of [DayProfile] for the Week,
 * Month and Year tabs.
 *
 * @property positions calendar values in display order: [Calendar.DAY_OF_WEEK]
 *   values from the locale's first day of the week, days of the month 1 to 31, or
 *   [Calendar.MONTH] values 0 to 11.
 * @property values share (0 to 1) of [samples] that had an entry, per position.
 * @property samples how many days (or months) of each position the window holds.
 * @property excludesCurrent true when the day (or month) still running was left out.
 */
class PositionProfile(
    val period: Period,
    val positions: IntArray,
    val values: DoubleArray,
    val samples: IntArray,
    val excludesCurrent: Boolean,
) {
    /** Index of the highest share (the first one on ties). */
    val peakIndex: Int = values.indices.maxByOrNull { values[it] } ?: 0

    /** The highest share, 0 when nothing was logged. */
    val peak: Double get() = values.getOrElse(peakIndex) { 0.0 }

    /** Days (or months) the profile is based on. */
    val units: Int get() = samples.sum()

    /** The fewest samples of any position: below a handful the shares are rough. */
    val minSamples: Int get() = samples.minOrNull() ?: 0

    companion object {
        /**
         * Builds the profile for [period] ([Period.WEEK], [Period.MONTH] or
         * [Period.YEAR]) from [sortedTimestamps] over `[from, to]`. Each day (each
         * month for [Period.YEAR]) counts once at its position, whatever its number
         * of entries. Days without entries count too. The day (month) still running
         * at [now] is left out unless it is the only one.
         */
        fun of(
            sortedTimestamps: LongArray,
            period: Period,
            from: Long,
            to: Long,
            now: Long,
            math: PeriodMath,
        ): PositionProfile {
            require(period != Period.DAY) { "Use DayProfile for the time of day" }
            val unit = if (period == Period.YEAR) Period.MONTH else Period.DAY
            val positions = when (period) {
                Period.WEEK -> IntArray(7) { (math.firstDayOfWeek - 1 + it) % 7 + 1 }
                Period.MONTH -> IntArray(31) { it + 1 }
                else -> IntArray(12) { it }
            }
            val field = when (period) {
                Period.WEEK -> Calendar.DAY_OF_WEEK
                Period.MONTH -> Calendar.DAY_OF_MONTH
                else -> Calendar.MONTH
            }
            val all = StatsEngine.bucketize(sortedTimestamps, unit, from, to, math)
            val used = all.filter { it.end <= now }.ifEmpty { all }

            val samples = IntArray(positions.size)
            val hits = IntArray(positions.size)
            for (bucket in used) {
                val index = positions.indexOf(math.field(bucket.start, field))
                samples[index]++
                if (bucket.count > 0) hits[index]++
            }
            val values = DoubleArray(positions.size) { if (samples[it] == 0) 0.0 else hits[it].toDouble() / samples[it] }
            return PositionProfile(period, positions, values, samples, excludesCurrent = used.size < all.size)
        }
    }
}
