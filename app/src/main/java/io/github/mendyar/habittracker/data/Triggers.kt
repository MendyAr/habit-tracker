package io.github.mendyar.habittracker.data

/**
 * Trigger tags: after logging a count habit the app suggests tagging what set it
 * off. These are the presets, from the research on what predicts the next
 * cigarette (craving, negative mood, other smokers) and common cues.
 */
object Triggers {
    val PRESETS = listOf("Craving", "Stress", "Bad Mood", "With Smokers", "Coffee", "Alcohol", "After a Meal", "Boredom", "Habit")

    /** Longest name accepted; longer names are cut at a word. */
    const val MAX_LENGTH = 30

    /** A typed trigger, tidied: one line, trimmed, at most [MAX_LENGTH] characters; null when empty. */
    fun clean(name: String): String? {
        val oneLine = name.replace(Regex("\\s+"), " ").trim()
        if (oneLine.isEmpty()) return null
        return if (oneLine.length <= MAX_LENGTH) oneLine else oneLine.take(MAX_LENGTH).substringBeforeLast(' ').ifEmpty { oneLine.take(MAX_LENGTH) }.trim()
    }

    /** [list] with [name] added at the end, unless it is already there (ignoring case). */
    fun added(list: List<String>, name: String): List<String> =
        if (list.any { it.equals(name, ignoreCase = true) }) list else list + name
}
