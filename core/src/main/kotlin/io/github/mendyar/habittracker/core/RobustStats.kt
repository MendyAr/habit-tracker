package io.github.mendyar.habittracker.core

import kotlin.math.abs
import kotlin.math.floor

/**
 * Statistics that one unusual day cannot pull around: habit counts are skewed
 * (most days are alike, a few are far higher), which inflates the mean and the
 * standard deviation. See docs/STATISTICS.md.
 */
object RobustStats {

    /** The [q] quantile (0 to 1) of [values] with linear interpolation; NaN when empty. */
    fun quantile(values: DoubleArray, q: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sortedArray()
        val position = q.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lower = floor(position).toInt()
        val upper = minOf(lower + 1, sorted.size - 1)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
    }

    fun median(values: DoubleArray): Double = quantile(values, 0.5)

    /** Mean after dropping [fraction] of the values at each end (rounded down); NaN when empty. */
    fun trimmedMean(values: DoubleArray, fraction: Double = 0.1): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sortedArray()
        val cut = floor(sorted.size * fraction).toInt()
        val kept = sorted.copyOfRange(cut, sorted.size - cut)
        return kept.average()
    }

    /** First and third quartile. */
    fun quartiles(values: DoubleArray): Pair<Double, Double> = quantile(values, 0.25) to quantile(values, 0.75)

    /** Median absolute deviation from the median. */
    fun mad(values: DoubleArray): Double {
        if (values.isEmpty()) return Double.NaN
        val m = median(values)
        return median(DoubleArray(values.size) { abs(values[it] - m) })
    }

    /** Tukey's upper fence, Q3 + 1.5 × IQR; NaN when empty. */
    fun upperFence(values: DoubleArray): Double {
        val (q1, q3) = quartiles(values)
        return q3 + 1.5 * (q3 - q1)
    }

    /**
     * Which [values] are outliers on the high side: above Q3 + 1.5 × IQR (Tukey's
     * fence). Needs at least 4 values; with fewer, nothing is flagged.
     */
    fun highOutliers(values: DoubleArray): BooleanArray {
        if (values.size < 4) return BooleanArray(values.size)
        val fence = upperFence(values)
        return BooleanArray(values.size) { values[it] > fence }
    }

    /** The mean of the [values] that [highOutliers] doesn't flag: the mean of ordinary days. */
    fun meanWithoutHighOutliers(values: DoubleArray): Double {
        val flagged = highOutliers(values)
        val kept = values.filterIndexed { i, _ -> !flagged[i] }
        return if (kept.isEmpty()) Double.NaN else kept.average()
    }

    /**
     * The median of each value and up to [window] − 1 values before it, e.g. the
     * median of the last 7 sessions at each session. Unlike [rollingMedian] the first
     * points use the values available.
     */
    fun trailingMedian(values: DoubleArray, window: Int = 7): DoubleArray = DoubleArray(values.size) { i ->
        median(values.copyOfRange(maxOf(0, i + 1 - window), i + 1))
    }

    /**
     * Trailing median over [window] values, e.g. the median of the last 7 days at
     * each day. Points with fewer than [window] values before them are NaN.
     */
    fun rollingMedian(values: DoubleArray, window: Int = 7): DoubleArray = DoubleArray(values.size) { i ->
        if (i + 1 < window) Double.NaN else median(values.copyOfRange(i + 1 - window, i + 1))
    }

    /**
     * Relative change of the last [n] values against the [n] before them, e.g.
     * -0.12 for 12% fewer; null without 2 × [n] values or when the earlier ones sum to 0.
     */
    fun change(values: DoubleArray, n: Int = 7): Double? {
        if (values.size < 2 * n) return null
        val recent = values.copyOfRange(values.size - n, values.size).sum()
        val before = values.copyOfRange(values.size - 2 * n, values.size - n).sum()
        return if (before == 0.0) null else (recent - before) / before
    }
}
