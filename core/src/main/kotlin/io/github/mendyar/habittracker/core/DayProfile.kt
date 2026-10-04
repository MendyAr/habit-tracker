package io.github.mendyar.habittracker.core

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * How likely a habit is at each time of day: [values] holds, for every
 * [stepMinutes] after midnight, the share of days (0 to 1) with an entry around
 * that time. 1 means "every day at this time", 0 "never near this time".
 *
 * @property days number of days the shares are based on.
 * @property excludesToday true when today was left out because it is not over yet.
 */
class DayProfile(
    val values: DoubleArray,
    val stepMinutes: Int,
    val days: Int,
    val excludesToday: Boolean,
) {
    /** Index of the highest value (the first one on ties). */
    val peakIndex: Int = values.indices.maxByOrNull { values[it] } ?: 0

    /** The highest share, 0 when nothing was logged. */
    val peak: Double get() = values.getOrElse(peakIndex) { 0.0 }

    /** Minutes after midnight of the value at [index]. */
    fun minuteAt(index: Int): Int = index * stepMinutes

    companion object {
        /** Resolution of the profile. */
        const val STEP_MINUTES = 5

        /**
         * Width of "around this time": an entry counts fully at its own minute,
         * half at about 24 minutes away, and hardly at all beyond an hour.
         */
        const val SPREAD_MINUTES = 20.0

        private const val DAY_MINUTES = 24 * 60

        /**
         * Builds the profile from [sortedTimestamps] over the local days that
         * intersect `[from, to]`. Today is left out while it is still running,
         * unless it is the only day.
         *
         * Each day contributes a smooth curve between 0 and 1: every entry adds a
         * bell curve of height 1 around its time of day, and overlapping curves of the
         * same day combine as "at least one entry around this time"
         * (`1 - Π(1 - bell)`), so a burst of entries counts as one day, not several.
         * The profile is the average of these curves over all days, including
         * days without entries. Time of day wraps around midnight.
         */
        fun of(
            sortedTimestamps: LongArray,
            from: Long,
            to: Long,
            now: Long,
            math: PeriodMath,
            stepMinutes: Int = STEP_MINUTES,
            spreadMinutes: Double = SPREAD_MINUTES,
        ): DayProfile {
            val all = StatsEngine.bucketize(sortedTimestamps, Period.DAY, from, to, math)
            val finished = all.filter { it.end <= now }
            val days = finished.ifEmpty { all }

            val points = DAY_MINUTES / stepMinutes
            val reach = ceil(4 * spreadMinutes / stepMinutes).toInt().coerceAtMost(points / 2)
            val twoSigmaSquared = 2 * spreadMinutes * spreadMinutes
            val sum = DoubleArray(points)
            val miss = DoubleArray(points)

            for (day in days) {
                if (day.count == 0) continue
                miss.fill(1.0)
                val first = StatsEngine.lowerBound(sortedTimestamps, day.start)
                for (i in first until first + day.count) {
                    val minute = math.minuteOfDay(sortedTimestamps[i])
                    val centre = (minute / stepMinutes).roundToInt()
                    for (offset in -reach..reach) {
                        val index = Math.floorMod(centre + offset, points)
                        val distance = circularDistance(index * stepMinutes.toDouble(), minute)
                        miss[index] *= 1 - exp(-distance * distance / twoSigmaSquared)
                    }
                }
                for (k in 0 until points) sum[k] += 1 - miss[k]
            }
            val values = DoubleArray(points) { if (days.isEmpty()) 0.0 else sum[it] / days.size }
            return DayProfile(values, stepMinutes, days.size, excludesToday = days.size < all.size)
        }

        /** Distance in minutes between two times of day, the short way around midnight. */
        private fun circularDistance(a: Double, b: Double): Double {
            val d = Math.abs(a - b) % DAY_MINUTES
            return minOf(d, DAY_MINUTES - d)
        }
    }
}
