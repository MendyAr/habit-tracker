package io.github.mendyar.habittracker.data

/** Rules for the sessions of duration habits, shared by icons and widgets. */
object Sessions {
    /** A session shorter than this is a double tap: the second tap leaves it running. */
    const val MIN_MS = 5_000L

    /** A session running longer than this when tapped was probably forgotten: ask first. */
    const val LONG_RUNNING_MS = 3 * 3_600_000L
}
