package io.github.mendyar.habittracker.core

/**
 * Times of day of the first and last entry of each day, and the gaps between
 * entries: when does the habit start and end, and how long does the user wait.
 */
object DailyTimes {

    /**
     * Minute of day (after midnight) of the first entry at or after [dayStartMinute]
     * on each finished day in `[from, to]` that has one. Entries before
     * [dayStartMinute] (late at night) are not a "first of the day".
     */
    fun firstOfDay(sortedTimestamps: LongArray, from: Long, to: Long, now: Long, math: PeriodMath, dayStartMinute: Int = 300): DoubleArray =
        perDay(sortedTimestamps, from, to, now, math) { minutes -> minutes.firstOrNull { it >= dayStartMinute } }

    /** Minute of day of the last entry of each finished day in `[from, to]` that has one. */
    fun lastOfDay(sortedTimestamps: LongArray, from: Long, to: Long, now: Long, math: PeriodMath): DoubleArray =
        perDay(sortedTimestamps, from, to, now, math) { minutes -> minutes.lastOrNull() }

    /** Minutes between consecutive entries in `[from, to]`. */
    fun gaps(sortedTimestamps: LongArray, from: Long, to: Long): DoubleArray {
        val start = StatsEngine.lowerBound(sortedTimestamps, from)
        val end = StatsEngine.lowerBound(sortedTimestamps, to + 1)
        if (end - start < 2) return DoubleArray(0)
        return DoubleArray(end - start - 1) { (sortedTimestamps[start + it + 1] - sortedTimestamps[start + it]) / 60_000.0 }
    }

    private fun perDay(
        sortedTimestamps: LongArray,
        from: Long,
        to: Long,
        now: Long,
        math: PeriodMath,
        pick: (List<Double>) -> Double?,
    ): DoubleArray {
        val days = StatsEngine.bucketize(sortedTimestamps, Period.DAY, from, to, math).filter { it.end <= now && it.count > 0 }
        return days.mapNotNull { day ->
            val first = StatsEngine.lowerBound(sortedTimestamps, day.start)
            pick((first until first + day.count).map { math.minuteOfDay(sortedTimestamps[it]) })
        }.toDoubleArray()
    }
}
