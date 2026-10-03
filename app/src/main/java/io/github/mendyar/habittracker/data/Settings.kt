package io.github.mendyar.habittracker.data

import android.content.Context
import android.content.SharedPreferences
import io.github.mendyar.habittracker.core.DateRange
import io.github.mendyar.habittracker.core.Period

/**
 * Persistent user preferences. Statistics settings are stored per habit, so each
 * habit remembers its own period and chart window across sessions.
 */
class Settings(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
    )

    /** The habit logged by the main launcher icon, or -1 when none is chosen. */
    var defaultHabitId: Long
        get() = prefs.getLong(KEY_DEFAULT_HABIT, -1)
        set(value) = prefs.edit().putLong(KEY_DEFAULT_HABIT, value).apply()

    /** Whether the first-run habit was set up; after that habits are never created implicitly. */
    var initialized: Boolean
        get() = prefs.getBoolean(KEY_INITIALIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_INITIALIZED, value).apply()

    /** The habit most recently shown in the statistics screen. */
    var lastViewedHabitId: Long
        get() = prefs.getLong(KEY_LAST_VIEWED, -1)
        set(value) = prefs.edit().putLong(KEY_LAST_VIEWED, value).apply()

    /** Whether the one-time "long-press for statistics" hint was shown. */
    var hintShown: Boolean
        get() = prefs.getBoolean(KEY_HINT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_HINT_SHOWN, value).apply()

    fun period(habitId: Long): Period = Period.fromName(prefs.getString("period_$habitId", null))

    fun setPeriod(habitId: Long, period: Period) {
        prefs.edit().putString("period_$habitId", period.name).apply()
    }

    fun range(habitId: Long): DateRange = DateRange(
        fromDay = prefs.getLong("range_from_$habitId", NONE).takeIf { it != NONE },
        toDay = prefs.getLong("range_to_$habitId", NONE).takeIf { it != NONE },
    )

    fun setRange(habitId: Long, range: DateRange) {
        prefs.edit()
            .putLong("range_from_$habitId", range.fromDay ?: NONE)
            .putLong("range_to_$habitId", range.toDay ?: NONE)
            .apply()
    }

    /** Habit shown by home-screen widget [widgetId], or -1 if unknown. */
    fun widgetHabit(widgetId: Int): Long = prefs.getLong("widget_$widgetId", -1)

    fun setWidgetHabit(widgetId: Int, habitId: Long) {
        prefs.edit().putLong("widget_$widgetId", habitId).apply()
    }

    fun removeWidget(widgetId: Int) {
        prefs.edit().remove("widget_$widgetId").apply()
    }

    /** Remembers that a widget for [habitId] was just requested from the editor. */
    fun setPendingWidget(habitId: Long, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_PENDING_WIDGET, habitId).putLong(KEY_PENDING_WIDGET_AT, now).apply()
    }

    /** The habit of a widget requested within the last [maxAgeMs], consuming it, or -1. */
    fun takePendingWidget(maxAgeMs: Long = PENDING_WIDGET_MAX_AGE_MS, now: Long = System.currentTimeMillis()): Long {
        val habitId = prefs.getLong(KEY_PENDING_WIDGET, -1)
        val at = prefs.getLong(KEY_PENDING_WIDGET_AT, 0)
        prefs.edit().remove(KEY_PENDING_WIDGET).remove(KEY_PENDING_WIDGET_AT).apply()
        return if (habitId != -1L && now - at in 0..maxAgeMs) habitId else -1
    }

    /** Removes every per-habit preference of a deleted habit. */
    fun forgetHabit(habitId: Long) {
        prefs.edit()
            .remove("period_$habitId")
            .remove("range_from_$habitId")
            .remove("range_to_$habitId")
            .apply()
    }

    private companion object {
        const val FILE = "settings"
        const val NONE = Long.MIN_VALUE
        const val KEY_DEFAULT_HABIT = "default_habit"
        const val KEY_LAST_VIEWED = "last_viewed_habit"
        const val KEY_HINT_SHOWN = "hint_shown"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_PENDING_WIDGET = "pending_widget"
        const val KEY_PENDING_WIDGET_AT = "pending_widget_at"
        const val PENDING_WIDGET_MAX_AGE_MS = 120_000L
    }
}
