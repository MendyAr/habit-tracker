package io.github.mendyar.habittracker.stats

import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.HabitType

/** The groups the statistics card is divided into, in display order. */
enum class StatGroup(val label: Int) {
    TYPICAL(R.string.group_typical),
    SPREAD(R.string.group_spread),
    TIMING(R.string.group_timing),
    PROGRESS(R.string.group_progress),
}

/**
 * Every statistic the statistics card can show: its label, a short note under it,
 * the explanation shown when the user taps it, and its group. Notes and
 * explanations name the habit's period (`%1$s` = "day", `%2$s` = "days", …).
 *
 * The defaults are the research-backed set for habits with occasional unusual days,
 * such as smoking (the "Habit tracking metrics" report); the rest can be added per
 * tracker. See docs/STATISTICS.md.
 */
enum class StatKind(
    val label: Int,
    val help: Int,
    val group: StatGroup,
    val byDefault: Boolean,
    val forDuration: Boolean = false,
    /** A short line under the label; null for none. */
    val note: Int? = null,
) {
    MEDIAN(R.string.stat_median, R.string.help_median, StatGroup.TYPICAL, true, note = R.string.note_median),
    TRIMMED_MEAN(R.string.stat_trimmed_mean, R.string.help_trimmed_mean, StatGroup.TYPICAL, true, note = R.string.note_trimmed_mean),
    MEAN(R.string.stat_average, R.string.help_mean, StatGroup.TYPICAL, true, note = R.string.note_mean),
    MEAN_TYPICAL(R.string.stat_mean_typical, R.string.help_mean_typical, StatGroup.TYPICAL, true, note = R.string.note_mean_typical),
    IQR(R.string.stat_iqr, R.string.help_iqr, StatGroup.SPREAD, true, note = R.string.note_iqr),
    STD_DEV(R.string.stat_std_dev, R.string.help_std_dev, StatGroup.SPREAD, true, note = R.string.note_std_dev),
    RANGE(R.string.stat_range, R.string.help_range, StatGroup.SPREAD, false),
    VARIANCE(R.string.stat_variance, R.string.help_variance, StatGroup.SPREAD, false),
    MIN(R.string.stat_min, R.string.help_min, StatGroup.SPREAD, false),
    MAX(R.string.stat_max, R.string.help_max, StatGroup.SPREAD, false),
    FIRST_ENTRY(R.string.stat_first_entry, R.string.help_first_entry, StatGroup.TIMING, false),
    LAST_ENTRY(R.string.stat_last_entry, R.string.help_last_entry, StatGroup.TIMING, false),
    SINCE_LAST(R.string.stat_since_last, R.string.help_since_last, StatGroup.TIMING, false),
    MEDIAN_GAP(R.string.stat_median_gap, R.string.help_median_gap, StatGroup.TIMING, false),
    CHANGE(R.string.stat_change, R.string.help_change, StatGroup.PROGRESS, false),
    WITHIN_TARGET(R.string.stat_within_target, R.string.help_within_target, StatGroup.PROGRESS, false),
    ZERO_PERIODS(R.string.stat_zero_periods, R.string.help_zero_periods, StatGroup.PROGRESS, false),
    TOTAL(R.string.stat_total, R.string.help_total, StatGroup.PROGRESS, false),

    // Duration trackers: statistics of the session lengths in the window.
    DUR_MEDIAN(R.string.stat_dur_median, R.string.help_dur_median, StatGroup.TYPICAL, true, forDuration = true, note = R.string.note_dur_median),
    DUR_IQR(R.string.stat_iqr, R.string.help_dur_iqr, StatGroup.SPREAD, true, forDuration = true, note = R.string.note_dur_iqr),
    DUR_MEAN(R.string.stat_dur_mean, R.string.help_dur_mean, StatGroup.TYPICAL, true, forDuration = true, note = R.string.note_dur_mean),
    UNDER_TARGET(R.string.stat_under_target, R.string.help_under_target, StatGroup.PROGRESS, true, forDuration = true, note = R.string.note_under_target),
    DUR_RANGE(R.string.stat_range, R.string.help_dur_range, StatGroup.SPREAD, false, forDuration = true),
    SESSIONS(R.string.stat_sessions, R.string.help_sessions, StatGroup.PROGRESS, false, forDuration = true),
    DUR_TOTAL(R.string.stat_total_time, R.string.help_total_time, StatGroup.PROGRESS, false, forDuration = true),
    ;

    companion object {
        /** Every statistic available to a habit of [type]. */
        fun available(type: HabitType): List<StatKind> = entries.filter { it.forDuration == (type == HabitType.DURATION) }

        /** The default set for a habit of [type]. */
        fun defaults(type: HabitType): List<StatKind> = available(type).filter { it.byDefault }

        fun fromName(name: String): StatKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The cards of the statistics screen below the count and the statistics, in
 * display order. Each is on or off per tracker; the defaults follow the research
 * report, the others can be added in Customize.
 */
enum class StatsCard(val label: Int, val forCount: Boolean, val forDuration: Boolean, val countDefault: Boolean, val durationDefault: Boolean) {
    /** One bar per period, unusually high ones flagged, with median and mean lines. */
    HISTORY(R.string.card_history, true, true, true, false),

    /** The share of all entries in each hour of the day. */
    HOURS(R.string.card_hours, true, true, true, false),

    /** Median, Q1 and Q3 of the time of the first entry of the day. */
    FIRST_ENTRY(R.string.card_first_entry, true, false, true, false),

    /** The median count of each weekday with its middle half. */
    WEEKDAYS(R.string.card_weekdays, true, false, true, false),

    /** Each session's length, with the median of the last 7 and the target. */
    SESSION_TREND(R.string.card_session_trend, false, true, false, true),

    /** Every session from shortest to longest. */
    DURATIONS(R.string.durations, false, true, false, false),

    /** How likely the habit is around each time of day (or weekday, day of month, month). */
    WHEN(R.string.card_when, true, true, false, false),

    /** The entries of the selected period. */
    ENTRIES(R.string.entries, true, true, true, true),
    ;

    fun availableFor(type: HabitType): Boolean = if (type == HabitType.DURATION) forDuration else forCount

    fun defaultFor(type: HabitType): Boolean = if (type == HabitType.DURATION) durationDefault else countDefault

    companion object {
        fun available(type: HabitType): List<StatsCard> = entries.filter { it.availableFor(type) }

        fun defaults(type: HabitType): Set<StatsCard> = entries.filter { it.defaultFor(type) }.toSet()

        fun fromName(name: String): StatsCard? = entries.firstOrNull { it.name == name }
    }
}

/**
 * What a tracker's statistics screen shows. The default is used until the user
 * customises it, and [isDefault] tells whether they did. (The period the screen
 * groups by is stored separately, as it also sets the count shown when logging.)
 *
 * @property stats the chosen statistics, or null for the default of the habit's type.
 * @property cards the chosen cards, or null for the default of the habit's type.
 * @property hiddenCards cards hidden from the default by version 2.0 (which showed
 *   every card unless hidden); only read from storage.
 * @property target a per-period target for [StatKind.WITHIN_TARGET] (count habits) or a
 *   number of minutes for [StatKind.UNDER_TARGET] (duration habits), or null.
 * @property dayStartHour where the time-of-day axis starts and after which an entry
 *   counts as the first of the day; null for the default (axis at 0:00, first after 5:00).
 */
data class StatsConfig(
    val stats: List<StatKind>? = null,
    val cards: Set<StatsCard>? = null,
    val target: Int? = null,
    val dayStartHour: Int? = null,
    val hiddenCards: Set<StatsCard> = emptySet(),
) {
    val isDefault: Boolean get() = this == StatsConfig()

    /** The statistics to show for a habit of [type]. */
    fun statsFor(type: HabitType): List<StatKind> = stats ?: StatKind.defaults(type)

    /** The cards to show for a habit of [type]. */
    fun cardsFor(type: HabitType): Set<StatsCard> =
        (cards ?: (StatsCard.defaults(type) - hiddenCards)).filter { it.availableFor(type) }.toSet()

    /** The target of [StatKind.UNDER_TARGET] in minutes: the chosen one, else [DEFAULT_SESSION_TARGET]. */
    val sessionTarget: Int get() = target ?: DEFAULT_SESSION_TARGET

    /** Compact text form for Settings, e.g. `s=MEDIAN,IQR;c=HISTORY,ENTRIES;t=8;d=4`. */
    fun encode(): String = listOfNotNull(
        "s=" + (stats?.joinToString(",") { it.name } ?: "*"),
        "c=" + (cards?.joinToString(",") { it.name } ?: "*"),
        "t=" + (target ?: ""),
        "d=" + (dayStartHour ?: ""),
        hiddenCards.takeIf { it.isNotEmpty() }?.let { hidden -> "h=" + hidden.joinToString(",") { it.name } },
    ).joinToString(";")

    companion object {
        /** The example target of the research report: a session under 5 minutes. */
        const val DEFAULT_SESSION_TARGET = 5

        /** Parses [encode]'s output; unknown names are skipped, null or garbage gives the default. */
        fun decode(text: String?): StatsConfig {
            if (text.isNullOrEmpty()) return StatsConfig()
            val parts = text.split(";").mapNotNull { part ->
                part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            return StatsConfig(
                stats = parts["s"]?.takeIf { it != "*" }?.split(",")?.mapNotNull { StatKind.fromName(it) },
                cards = parts["c"]?.takeIf { it != "*" }?.split(",")?.mapNotNull { StatsCard.fromName(it) }?.toSet(),
                target = parts["t"]?.toIntOrNull(),
                dayStartHour = parts["d"]?.toIntOrNull()?.takeIf { it in 0..23 },
                hiddenCards = parts["h"]?.split(",")?.mapNotNull { StatsCard.fromName(it) }?.toSet() ?: emptySet(),
            )
        }
    }
}
