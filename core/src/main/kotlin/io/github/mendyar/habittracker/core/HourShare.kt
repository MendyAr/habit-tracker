package io.github.mendyar.habittracker.core

/**
 * Of all entries in a window, the share that fell in each clock hour: where in
 * the day the habit happens, and so where a nudge or a replacement ritual would
 * help most. Unlike [DayProfile] (the share of days with an entry around each
 * time), every entry counts, so a busy hour weighs more.
 *
 * @property counts entries per hour, 0:00–0:59 first.
 * @property shares each hour's share (0 to 1) of all entries; all 0 when there are none.
 */
class HourShare(val counts: IntArray, val shares: DoubleArray) {

    /** Number of entries in the window. */
    val total: Int get() = counts.sum()

    /** The [n] busiest hours (with entries), busiest first; earlier hours win ties. */
    fun top(n: Int = 3): List<Int> = counts.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }.take(n)

    /** The combined share of [hours]. */
    fun shareOf(hours: Collection<Int>): Double = hours.sumOf { shares[it] }

    companion object {
        /** Builds it from [sortedTimestamps] in `[from, to]`. */
        fun of(sortedTimestamps: LongArray, from: Long, to: Long, math: PeriodMath): HourShare {
            val counts = IntArray(24)
            val start = StatsEngine.lowerBound(sortedTimestamps, from)
            val end = StatsEngine.lowerBound(sortedTimestamps, to + 1)
            for (i in start until end) counts[(math.minuteOfDay(sortedTimestamps[i]) / 60).toInt().coerceIn(0, 23)]++
            val total = counts.sum()
            return HourShare(counts, DoubleArray(24) { if (total == 0) 0.0 else counts[it].toDouble() / total })
        }
    }
}
