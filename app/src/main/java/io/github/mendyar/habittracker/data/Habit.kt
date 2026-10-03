package io.github.mendyar.habittracker.data

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
)

/** A single logged timestamp. */
data class Entry(val id: Long, val habitId: Long, val timestamp: Long)
