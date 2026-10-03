package io.github.mendyar.habittracker.launcher

import android.content.Context
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.widget.HabitWidgetProvider

/** Keeps everything shown on the home screen in step with the database. */
object LauncherSync {

    /**
     * After habits were created, edited or deleted: refreshes long-press shortcuts,
     * pinned shortcuts and every widget. Call off the main thread.
     */
    fun refresh(context: Context) {
        val app = context.applicationContext
        syncShortcuts(app)
        HabitWidgetProvider.updateAll(app)
    }

    /**
     * After a timestamp was logged: only re-orders the long-press shortcuts. Widgets
     * look the same, so they are not redrawn (that would make every widget flicker).
     * Call off the main thread.
     */
    fun afterLog(context: Context) {
        syncShortcuts(context.applicationContext)
    }

    private fun syncShortcuts(app: Context) {
        val repository = HabitRepository.get(app)
        val lastLogged = repository.lastLogged()
        val ordered = repository.habits().sortedByDescending { lastLogged[it.id] ?: it.createdAt }
        Shortcuts.sync(app, ordered)
    }
}
