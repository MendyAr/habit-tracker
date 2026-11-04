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

    /**
     * Which [values] are outliers on the high side: above Q3 + 1.5 × IQR (Tukey's
     * fence). Needs at least 4 values; with fewer, nothing is flagged.
     */
    fun highOutliers(values: DoubleArray): BooleanArray {
        if (values.size < 4) return BooleanArray(values.size)
        val (q1, q3) = quartiles(values)
        val fence = q3 + 1.5 * (q3 - q1)
        return BooleanArray(values.size) { values[it] > fence }
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
