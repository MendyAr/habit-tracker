package io.github.mendyar.habittracker.core

/** The granularity statistics are aggregated by. */
enum class Period {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    companion object {
        /** Parses a persisted [name], falling back to [DAY] for unknown or missing values. */
        fun fromName(name: String?): Period = entries.firstOrNull { it.name == name } ?: DAY
    }
}
