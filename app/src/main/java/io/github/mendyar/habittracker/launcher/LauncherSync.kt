package io.github.mendyar.habittracker.launcher

import android.content.Context
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.widget.HabitWidgetProvider

/** Keeps everything shown on the home screen in step with the database. */
object LauncherSync {

    /**
     * Refreshes long-press shortcuts, pinned shortcuts and widgets. Must be called
     * off the main thread after habits or entries changed.
     */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val repository = HabitRepository.get(app)
        repository.defaultHabit()
        val lastLogged = repository.lastLogged()
        val ordered = repository.habits().sortedByDescending { lastLogged[it.id] ?: it.createdAt }
        Shortcuts.sync(app, ordered)
        HabitWidgetProvider.updateAll(app)
    }
}
