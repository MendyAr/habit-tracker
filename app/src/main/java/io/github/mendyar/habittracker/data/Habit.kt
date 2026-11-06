package io.github.mendyar.habittracker.data

/** What tapping a habit records. */
enum class HabitType {
    /** Each tap logs a moment. */
    COUNT,

    /** The first tap starts a session, the next one finishes it; the duration is recorded. */
    DURATION,
}

/**
 * A tracked habit.
 *
 * @property icon key of a built-in icon (see [io.github.mendyar.habittracker.icons.HabitIcons]).
 * @property customIcon absolute path of a user-supplied image that replaces [icon], or null.
 * @property color ARGB accent colour used behind the icon glyph and in charts.
 */
data class Habit(
    val id: Long,
    val name: String,
    val icon: String,
    val customIcon: String?,
    val color: Int,
    val createdAt: Long,
    val type: HabitType = HabitType.COUNT,
)

/**
 * A single logged timestamp. For a [HabitType.DURATION] habit it is the start of a
 * session, and [duration] (ms) is null while the session is still running.
 * [trigger] is what set it off, if the user said (count habits).
 */
data class Entry(val id: Long, val habitId: Long, val timestamp: Long, val duration: Long? = null, val trigger: String? = null) {
    /** When a finished session ended; null while it runs (or for a moment). */
    val end: Long? get() = duration?.let { timestamp + it }
}
