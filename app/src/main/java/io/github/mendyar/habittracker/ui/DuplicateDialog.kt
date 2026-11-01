package io.github.mendyar.habittracker.ui

import android.app.Activity
import android.app.AlertDialog
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository

/**
 * Asks before a log that looks like a mistake: the same habit logged moments ago,
 * or a manual entry in a minute that already has one. "Don't Ask Again" turns the
 * question off for that habit.
 */
object DuplicateDialog {

    /** [habit] was logged at [previous], moments before [now]: log it again? */
    fun recent(activity: Activity, habit: Habit, previous: Long, now: Long, onLog: () -> Unit, onCancel: () -> Unit): AlertDialog {
        val seconds = ((now - previous) / 1000).toInt().coerceAtLeast(0)
        val message = activity.getString(
            R.string.duplicate_message,
            habit.name,
            Formats(activity).time(previous),
            activity.resources.getQuantityString(R.plurals.seconds_ago, seconds, seconds),
        )
        return show(activity, habit, message, R.string.log_again, onLog, onCancel)
    }

    /** [habit] already has an entry in the minute of [timestamp]: add another? */
    fun sameMinute(activity: Activity, habit: Habit, timestamp: Long, onAdd: () -> Unit): AlertDialog {
        val message = activity.getString(R.string.duplicate_manual_message, habit.name, Formats(activity).dateTime(timestamp))
        return show(activity, habit, message, R.string.add, onAdd, {})
    }

    private fun show(activity: Activity, habit: Habit, message: String, confirm: Int, onLog: () -> Unit, onCancel: () -> Unit): AlertDialog =
        AlertDialog.Builder(activity, R.style.Theme_HabitTracker_Dialog)
            .setTitle(R.string.duplicate_title)
            .setMessage(message)
            .setPositiveButton(confirm) { _, _ -> onLog() }
            .setNeutralButton(R.string.dont_ask_again) { _, _ ->
                val app = activity.applicationContext
                Async.io { HabitRepository.get(app).settings.setConfirmDuplicates(habit.id, false) }
                onLog()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
}
