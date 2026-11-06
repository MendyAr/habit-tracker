package io.github.mendyar.habittracker.stats

import android.content.Context
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.Bucket
import io.github.mendyar.habittracker.core.DailyTimes
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.core.RobustStats
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.ui.Formats
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Computes the text of each [StatKind] for one tracker over the chart window, and
 * the short note shown under it. Per-period statistics use the finished periods
 * (the one still running is left out unless it is the only one), like
 * [StatsEngine.summarize]. Notes and explanations name [period] ("day", "week"…).
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
    /** Lengths (ms) of the finished sessions in the window, for duration habits. */
    private val durations: LongArray = LongArray(0),
    private val period: Period = Period.DAY,
) {
    private val minutes = DoubleArray(durations.size) { durations[it] / 60_000.0 }
    private val finished = buckets.filter { it.end <= now }.ifEmpty { buckets }
    private val counts = DoubleArray(finished.size) { finished[it].count.toDouble() }
    private val empty = context.getString(R.string.stat_empty)
    private val unit = formats.unit(period)
    private val units = formats.units(period)

    /** How many periods the statistics are based on. */
    val periods: Int get() = finished.size

    /** Whether the period still running was left out. */
    val excludesCurrent: Boolean get() = finished.size < buckets.size

    fun text(kind: StatKind): String {
        if (kind.forDuration) return durationText(kind)
        if (counts.isEmpty() && kind != StatKind.SINCE_LAST) return empty
        return when (kind) {
            StatKind.MEDIAN -> number(RobustStats.median(counts))
            StatKind.TRIMMED_MEAN -> number(RobustStats.trimmedMean(counts))
            StatKind.IQR -> RobustStats.quartiles(counts).let { (q1, q3) -> number(q3 - q1) }
            StatKind.MEAN_TYPICAL -> number(RobustStats.meanWithoutHighOutliers(counts))
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
            StatKind.FIRST_ENTRY -> timeOfDay(firstEntries())
            StatKind.LAST_ENTRY -> timeOfDay(DailyTimes.lastOfDay(timestamps, bounds.first, bounds.last, now, math))
            StatKind.MEDIAN_GAP -> DailyTimes.gaps(timestamps, bounds.first, bounds.last).let { gaps ->
                if (gaps.isEmpty()) empty else formats.duration(RobustStats.median(gaps))
            }
            StatKind.SINCE_LAST -> timestamps.lastOrNull { it <= now }?.let { formats.duration((now - it) / 60_000.0) } ?: empty
            else -> empty
        }
    }

    /** The short line under the value, e.g. "Pulled up by heavy days"; null for none. */
    fun note(kind: StatKind): String? {
        val note = kind.note ?: return null
        val extra: String = when (kind) {
            StatKind.IQR -> if (counts.isEmpty()) return null else RobustStats.quartiles(counts).let { (q1, q3) -> range(number(q1), number(q3)) }
            StatKind.MEAN_TYPICAL -> if (counts.isEmpty()) return null else formats.number(RobustStats.highOutliers(counts).count { it })
            StatKind.DUR_IQR -> if (minutes.isEmpty()) return null else RobustStats.quartiles(minutes).let { (q1, q3) -> range(length(q1), length(q3)) }
            StatKind.UNDER_TARGET -> formats.duration(config.sessionTarget.toDouble())
            else -> ""
        }
        return context.getString(note, unit, units, extra)
    }

    /** The name of [kind], naming this tracker's period where it needs to (e.g. "Mean without flagged days"). */
    fun label(kind: StatKind): String = context.getString(kind.label, unit, units)

    /** The explanation of [kind], naming this tracker's period. */
    fun help(kind: StatKind): String = context.getString(kind.help, unit, units)

    /** Minutes of day of each day's first entry, after the day starts. */
    fun firstEntries(): DoubleArray =
        DailyTimes.firstOfDay(timestamps, bounds.first, bounds.last, now, math, (config.dayStartHour ?: FIRST_ENTRY_HOUR) * 60)

    /** Statistics of the session lengths (duration habits). */
    private fun durationText(kind: StatKind): String {
        if (kind == StatKind.SESSIONS) return formats.number(durations.size)
        if (minutes.isEmpty()) return empty
        return when (kind) {
            StatKind.DUR_MEDIAN -> length(RobustStats.median(minutes))
            StatKind.DUR_IQR -> RobustStats.quartiles(minutes).let { (q1, q3) -> length(q3 - q1) }
            StatKind.DUR_RANGE -> range(length(minutes.min()), length(minutes.max()))
            StatKind.DUR_MEAN -> length(minutes.average())
            StatKind.DUR_TOTAL -> formats.duration(minutes.sum())
            StatKind.UNDER_TARGET -> formats.percent(minutes.count { it < config.sessionTarget }.toDouble() / minutes.size)
            else -> empty
        }
    }

    private fun length(m: Double) = formats.elapsed((m * 60_000).toLong())

    /** The sample variance (n − 1), as in the research report; 0 for a single period. */
    private fun variance(): Double {
        if (counts.size < 2) return 0.0
        val mean = counts.average()
        return counts.sumOf { (it - mean) * (it - mean) } / (counts.size - 1)
    }

    private fun timeOfDay(minutes: DoubleArray): String =
        if (minutes.isEmpty()) empty else formats.timeOfDay(RobustStats.median(minutes).roundToInt())

    private fun number(value: Double): String = if (value.isNaN()) empty else formats.number(value)

    private fun range(low: String, high: String): String = context.getString(R.string.value_range, low, high)

    companion object {
        /** By default the first entry of the day is the first one after 5:00. */
        const val FIRST_ENTRY_HOUR = 5
        private const val TARGET_PERIODS = 30
    }
}
