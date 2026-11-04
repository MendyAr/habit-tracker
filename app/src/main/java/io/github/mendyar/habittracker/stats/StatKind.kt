package io.github.mendyar.habittracker.stats

import io.github.mendyar.habittracker.R

/**
 * Every statistic the statistics card can show, with its label and a one- or
 * two-line explanation (shown when the user taps it). The defaults are the
 * research-backed set for habits with occasional unusual days, such as smoking;
 * the rest can be added per tracker. See docs/STATISTICS.md.
 */
enum class StatKind(val label: Int, val help: Int, val byDefault: Boolean) {
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
    ;

    companion object {
        val DEFAULTS: List<StatKind> = entries.filter { it.byDefault }

        fun fromName(name: String): StatKind? = entries.firstOrNull { it.name == name }
    }
}

/** The cards of the statistics screen that a tracker can hide. */
enum class StatsCard { HISTORY, WHEN, ENTRIES }

/**
 * What a tracker's statistics screen shows. The default is used until the user
 * customises it, and [isDefault] tells whether they did.
 *
 * @property target a per-period target for [StatKind.WITHIN_TARGET], or null.
 * @property dayStartHour where the time-of-day axis starts and after which an entry
 *   counts as the first of the day; null for the default (axis at 0:00, first after 5:00).
 */
data class StatsConfig(
    val stats: List<StatKind> = StatKind.DEFAULTS,
    val hiddenCards: Set<StatsCard> = emptySet(),
    val target: Int? = null,
    val dayStartHour: Int? = null,
) {
    val isDefault: Boolean get() = this == StatsConfig()

    /** Compact text form for Settings, e.g. `s=MEDIAN,IQR;h=WHEN;t=8;d=4`. */
    fun encode(): String = listOf(
        "s=" + stats.joinToString(",") { it.name },
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
                stats = parts["s"]?.split(",")?.mapNotNull { StatKind.fromName(it) } ?: StatKind.DEFAULTS,
                hiddenCards = parts["h"]?.split(",")?.mapNotNull { n -> StatsCard.entries.firstOrNull { it.name == n } }?.toSet()
                    ?: emptySet(),
                target = parts["t"]?.toIntOrNull(),
                dayStartHour = parts["d"]?.toIntOrNull()?.takeIf { it in 0..23 },
            )
        }
    }
}
