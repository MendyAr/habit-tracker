package io.github.mendyar.habittracker.core

import kotlin.math.sqrt

/** Number of timestamps in the half-open interval [[start], [end]). */
data class Bucket(val start: Long, val end: Long, val count: Int)

/**
 * Descriptive statistics of per-period counts.
 *
 * @property periods number of periods the statistics are based on.
 * @property total number of timestamps in the whole range (including any period
 *   left out of the statistics because it is still in progress).
 * @property variance population variance of the per-period counts.
 * @property excludesCurrent true when the still-running period (e.g. today) was left
 *   out so that a half-finished day does not drag the average and minimum down.
 */
data class Summary(
    val periods: Int,
    val total: Int,
    val average: Double,
    val min: Int,
    val max: Int,
    val variance: Double,
    val stdDev: Double,
    val excludesCurrent: Boolean,
)

/**
 * A user-chosen date window. Both ends are inclusive local days, given as any
 * millisecond inside that day; `null` means "open" (first entry / now).
 */
data class DateRange(val fromDay: Long? = null, val toDay: Long? = null) {
    val isAll: Boolean get() = fromDay == null && toDay == null

    companion object {
        val ALL = DateRange()
    }
}

/** Pure functions that turn raw timestamps into chartable buckets and statistics. */
object StatsEngine {

    /**
     * Resolves [range] to concrete millisecond bounds `[from, to]` (both inclusive).
     *
     * An open start falls back to the first timestamp (or [now] when there is none);
     * an open end, or an end in the future, is clamped to [now].
     */
    fun resolve(range: DateRange, firstTimestamp: Long?, now: Long, math: PeriodMath): LongRange {
        val from = range.fromDay?.let { math.startOf(Period.DAY, it) } ?: firstTimestamp ?: now
        val to = range.toDay?.let { minOf(math.endOf(Period.DAY, it) - 1, now) } ?: now
        return if (from <= to) from..to else to..from
    }

    /**
     * Groups [sortedTimestamps] into consecutive [period] buckets covering every
     * period that intersects `[from, to]`, including empty ones.
     */
    fun bucketize(
        sortedTimestamps: LongArray,
        period: Period,
        from: Long,
        to: Long,
        math: PeriodMath,
    ): List<Bucket> {
        val result = ArrayList<Bucket>()
        var start = math.startOf(period, from)
        val lastStart = math.startOf(period, to)
        var index = lowerBound(sortedTimestamps, start)
        while (start <= lastStart) {
            val end = math.shift(period, start, 1)
            var count = 0
            while (index < sortedTimestamps.size && sortedTimestamps[index] < end) {
                count++
                index++
            }
            result.add(Bucket(start, end, count))
            start = end
        }
        return result
    }

    /**
     * Summarises per-period counts. Periods that have not finished yet at [now]
     * are excluded unless no finished period exists. Returns `null` for no buckets.
     */
    fun summarize(buckets: List<Bucket>, now: Long): Summary? {
        if (buckets.isEmpty()) return null
        val finished = buckets.filter { it.end <= now }
        val used = finished.ifEmpty { buckets }
        val n = used.size
        val average = used.sumOf { it.count }.toDouble() / n
        val variance = used.sumOf { (it.count - average) * (it.count - average) } / n
        return Summary(
            periods = n,
            total = buckets.sumOf { it.count },
            average = average,
            min = used.minOf { it.count },
            max = used.maxOf { it.count },
            variance = variance,
            stdDev = sqrt(variance),
            excludesCurrent = n < buckets.size,
        )
    }

    /** Number of [sortedTimestamps] inside [[start], [end]). */
    fun countBetween(sortedTimestamps: LongArray, start: Long, end: Long): Int =
        lowerBound(sortedTimestamps, end) - lowerBound(sortedTimestamps, start)

    /** Index of the first element `>= value` in [sorted]. */
    fun lowerBound(sorted: LongArray, value: Long): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * A "nice" axis maximum `>= value` from the 1-2-5 series (1, 2, 5, 10, 20, ...),
     * so chart grid lines land on round numbers. Returns 1 for values below 1.
     */
    fun niceCeiling(value: Int): Int {
        if (value <= 1) return 1
        var magnitude = 1
        while (true) {
            for (step in intArrayOf(1, 2, 5)) {
                val candidate = step * magnitude
                if (candidate >= value) return candidate
            }
            magnitude *= 10
        }
    }
}
