package io.github.mendyar.habittracker.stats

import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.HabitType

/**
 * Every statistic the statistics card can show, with its label and a one- or
 * two-line explanation (shown when the user taps it). The defaults are the
 * research-backed set for habits with occasional unusual days, such as smoking;
 * the rest can be added per tracker. See docs/STATISTICS.md.
 */
enum class StatKind(val label: Int, val help: Int, val byDefault: Boolean, val forDuration: Boolean = false) {
    MEDIAN(R.string.stat_median, R.string.help_median, true),
    TRIMMED_MEAN(R.string.stat_trimmed_mean, R.string.help_trimmed_mean, true),
    IQR(R.string.stat_iqr, R.string.help_iqr, true),
    RANGE(R.string.stat_range, R.string.help_range, true),
    FIRST_ENTRY(R.string.stat_first_entry, R.string.help_first_entry, true),
    SINCE_LAST(R.string.stat_since_last, R.string.help_since_last, true),
    CHANGE(R.string.stat_change, R.string.help_change, true),
    MEAN(R.string.stat_average, R.string.help_mean, false),
    STD_DEV(R.string.stat_std_dev, R.string.help_std_dev, false),
    VARIANCE(R.string.stat_variance, R.string.help_variance, false),
    MIN(R.string.stat_min, R.string.help_min, false),
    MAX(R.string.stat_max, R.string.help_max, false),
    TOTAL(R.string.stat_total, R.string.help_total, false),
    ZERO_PERIODS(R.string.stat_zero_periods, R.string.help_zero_periods, false),
    WITHIN_TARGET(R.string.stat_within_target, R.string.help_within_target, false),
    MEDIAN_GAP(R.string.stat_median_gap, R.string.help_median_gap, false),
    LAST_ENTRY(R.string.stat_last_entry, R.string.help_last_entry, false),

    // Duration trackers: statistics of the session lengths in the window.
    DUR_MEDIAN(R.string.stat_dur_median, R.string.help_dur_median, true, forDuration = true),
    DUR_IQR(R.string.stat_iqr, R.string.help_dur_iqr, true, forDuration = true),
    DUR_RANGE(R.string.stat_range, R.string.help_dur_range, true, forDuration = true),
    SESSIONS(R.string.stat_sessions, R.string.help_sessions, true, forDuration = true),
    UNDER_TARGET(R.string.stat_under_target, R.string.help_under_target, true, forDuration = true),
    DUR_MEAN(R.string.stat_average, R.string.help_dur_mean, false, forDuration = true),
    DUR_TOTAL(R.string.stat_total_time, R.string.help_total_time, false, forDuration = true),
    ;

    companion object {
        /** Every statistic available to a habit of [type]. */
        fun available(type: HabitType): List<StatKind> = entries.filter { it.forDuration == (type == HabitType.DURATION) }

        /** The default set for a habit of [type]. */
        fun defaults(type: HabitType): List<StatKind> = available(type).filter { it.byDefault }

        fun fromName(name: String): StatKind? = entries.firstOrNull { it.name == name }
    }
}

/** The cards of the statistics screen that a tracker can hide. */
enum class StatsCard { HISTORY, DURATIONS, WHEN, ENTRIES }

/**
 * What a tracker's statistics screen shows. The default is used until the user
 * customises it, and [isDefault] tells whether they did.
 *
 * @property stats the chosen statistics, or null for the default of the habit's type.
 * @property target a per-period target for [StatKind.WITHIN_TARGET] (count habits) or a
 *   number of minutes for [StatKind.UNDER_TARGET] (duration habits), or null.
 * @property dayStartHour where the time-of-day axis starts and after which an entry
 *   counts as the first of the day; null for the default (axis at 0:00, first after 5:00).
 */
data class StatsConfig(
    val stats: List<StatKind>? = null,
    val hiddenCards: Set<StatsCard> = emptySet(),
    val target: Int? = null,
    val dayStartHour: Int? = null,
) {
    val isDefault: Boolean get() = this == StatsConfig()

    /** The statistics to show for a habit of [type]. */
    fun statsFor(type: HabitType): List<StatKind> = stats ?: StatKind.defaults(type)

    /** Compact text form for Settings, e.g. `s=MEDIAN,IQR;h=WHEN;t=8;d=4`. */
    fun encode(): String = listOf(
        "s=" + (stats?.joinToString(",") { it.name } ?: "*"),
        "h=" + hiddenCards.joinToString(",") { it.name },
        "t=" + (target ?: ""),
        "d=" + (dayStartHour ?: ""),
    ).joinToString(";")

    companion object {
        /** Parses [encode]'s output; unknown names are skipped, null or garbage gives the default. */
        fun decode(text: String?): StatsConfig {
            if (text.isNullOrEmpty()) return StatsConfig()
            val parts = text.split(";").mapNotNull { part ->
                part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            return StatsConfig(
                stats = parts["s"]?.takeIf { it != "*" }?.split(",")?.mapNotNull { StatKind.fromName(it) },
                hiddenCards = parts["h"]?.split(",")?.mapNotNull { n -> StatsCard.entries.firstOrNull { it.name == n } }?.toSet()
                    ?: emptySet(),
                target = parts["t"]?.toIntOrNull(),
                dayStartHour = parts["d"]?.toIntOrNull()?.takeIf { it in 0..23 },
            )
        }
    }
}
