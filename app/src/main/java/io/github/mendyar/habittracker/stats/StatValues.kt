package io.github.mendyar.habittracker.stats

import android.content.Context
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.Bucket
import io.github.mendyar.habittracker.core.DailyTimes
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.core.RobustStats
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.ui.Formats
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Computes the text of each [StatKind] for one tracker over the chart window.
 * Per-period statistics use the finished periods (the one still running is left
 * out unless it is the only one), like [StatsEngine.summarize].
 */
class StatValues(
    private val context: Context,
    private val formats: Formats,
    private val timestamps: LongArray,
    private val buckets: List<Bucket>,
    private val bounds: LongRange,
    private val now: Long,
    private val math: PeriodMath,
    private val config: StatsConfig,
) {
    private val finished = buckets.filter { it.end <= now }.ifEmpty { buckets }
    private val counts = DoubleArray(finished.size) { finished[it].count.toDouble() }
    private val empty = context.getString(R.string.stat_empty)

    /** How many periods the statistics are based on. */
    val periods: Int get() = finished.size

    /** Whether the period still running was left out. */
    val excludesCurrent: Boolean get() = finished.size < buckets.size

    fun text(kind: StatKind): String {
        if (counts.isEmpty() && kind != StatKind.SINCE_LAST) return empty
        return when (kind) {
            StatKind.MEDIAN -> number(RobustStats.median(counts))
            StatKind.TRIMMED_MEAN -> number(RobustStats.trimmedMean(counts))
            StatKind.IQR -> RobustStats.quartiles(counts).let { (q1, q3) -> range(number(q1), number(q3)) }
            StatKind.RANGE -> range(number(counts.min()), number(counts.max()))
            StatKind.MEAN -> number(counts.average())
            StatKind.STD_DEV -> number(sqrt(variance()))
            StatKind.VARIANCE -> number(variance())
            StatKind.MIN -> number(counts.min())
            StatKind.MAX -> number(counts.max())
            StatKind.TOTAL -> formats.number(buckets.sumOf { it.count })
            StatKind.ZERO_PERIODS -> context.getString(R.string.value_of, formats.number(finished.count { it.count == 0 }), formats.number(finished.size))
            StatKind.WITHIN_TARGET -> config.target?.let { target ->
                val recent = finished.takeLast(TARGET_PERIODS)
                context.getString(R.string.value_of, formats.number(recent.count { it.count <= target }), formats.number(recent.size))
            } ?: empty
            StatKind.CHANGE -> RobustStats.change(counts)?.let { change ->
                val sign = if (change > 0) "+" else if (change < 0) "−" else ""
                context.getString(R.string.value_percent_change, sign, formats.percent(kotlin.math.abs(change)))
            } ?: empty
            StatKind.FIRST_ENTRY -> timeOfDay(
                DailyTimes.firstOfDay(timestamps, bounds.first, bounds.last, now, math, (config.dayStartHour ?: FIRST_ENTRY_HOUR) * 60),
            )
            StatKind.LAST_ENTRY -> timeOfDay(DailyTimes.lastOfDay(timestamps, bounds.first, bounds.last, now, math))
            StatKind.MEDIAN_GAP -> DailyTimes.gaps(timestamps, bounds.first, bounds.last).let { gaps ->
                if (gaps.isEmpty()) empty else formats.duration(RobustStats.median(gaps))
            }
            StatKind.SINCE_LAST -> timestamps.lastOrNull { it <= now }?.let { formats.duration((now - it) / 60_000.0) } ?: empty
        }
    }

    private fun variance(): Double {
        val mean = counts.average()
        return counts.sumOf { (it - mean) * (it - mean) } / counts.size
    }

    private fun timeOfDay(minutes: DoubleArray): String =
        if (minutes.isEmpty()) empty else formats.timeOfDay(RobustStats.median(minutes).roundToInt())

    private fun number(value: Double): String = if (value.isNaN()) empty else formats.number(value)

    private fun range(low: String, high: String): String = context.getString(R.string.value_range, low, high)

    private companion object {
        /** By default the first entry of the day is the first one after 5:00. */
        const val FIRST_ENTRY_HOUR = 5
        const val TARGET_PERIODS = 30
    }
}
