package io.github.mendyar.habittracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.icons.HabitIconDrawable
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.Formats

/**
 * A 1×1 home-screen widget per habit. Tapping it logs a timestamp entirely in
 * place: no window opens, the widget itself flips to a check with the count and
 * back again.
 */
class HabitWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Async.io {
            try {
                appWidgetIds.forEach { update(context, manager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val settings = HabitRepository.get(context).settings
        appWidgetIds.forEach { settings.removeWidget(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_LOG -> logFromWidget(context, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            ACTION_PINNED -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val habitId = intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1)
                if (widgetId == -1 || habitId == -1L) return
                val pending = goAsync()
                Async.io {
                    try {
                        bind(context.applicationContext, widgetId, habitId)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun logFromWidget(context: Context, widgetId: Int) {
        if (widgetId == -1) return
        val pending = goAsync()
        val app = context.applicationContext
        Async.io {
            val repository = HabitRepository.get(app)
            val habit = repository.habitOrDefault(repository.settings.widgetHabit(widgetId))
            val now = System.currentTimeMillis()
            repository.log(habit.id, now)
            val period = repository.settings.period(habit.id)
            val math = PeriodMath()
            val count = repository.count(habit.id, math.startOf(period, now), math.endOf(period, now))

            val manager = AppWidgetManager.getInstance(app)
            val done = RemoteViews(app.packageName, R.layout.widget_habit).apply {
                setImageViewBitmap(R.id.widget_done, doneBitmap(app, habit))
                setTextViewText(R.id.widget_count, Formats(app).countPhrase(period, count))
                setDisplayedChild(R.id.widget_flipper, 1)
            }
            manager.partiallyUpdateAppWidget(widgetId, done)

            Async.mainDelayed(CONFIRM_MS) {
                Async.io {
                    try {
                        // Flips every widget back and refreshes shortcuts with the new ordering.
                        LauncherSync.refresh(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        internal const val ACTION_LOG = "io.github.mendyar.habittracker.action.WIDGET_LOG"
        private const val ACTION_PINNED = "io.github.mendyar.habittracker.action.WIDGET_PINNED"
        private const val CONFIRM_MS = 1400L

        /** Redraws every widget. Call off the main thread. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, HabitWidgetProvider::class.java))
                .forEach { update(context, manager, it) }
        }

        /** Associates [widgetId] with [habitId] and redraws it. Call off the main thread. */
        fun bind(context: Context, widgetId: Int, habitId: Long) {
            HabitRepository.get(context).settings.setWidgetHabit(widgetId, habitId)
            update(context, AppWidgetManager.getInstance(context), widgetId)
        }

        /**
         * Asks the launcher to place a widget for [habit] (Android 8+). Returns
         * false when the launcher does not support it.
         */
        fun requestPin(context: Context, habit: Habit): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            val callback = Intent(ACTION_PINNED)
                .setClass(context, HabitWidgetProvider::class.java)
                .putExtra(Shortcuts.EXTRA_HABIT_ID, habit.id)
            // Mutable: the system adds the new widget id to this intent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, habit.id.toInt(), callback, flags)
            return manager.requestPinAppWidget(ComponentName(context, HabitWidgetProvider::class.java), Bundle(), pending)
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val repository = HabitRepository.get(context)
            val habit = repository.habitOrDefault(repository.settings.widgetHabit(widgetId))
            val tap = Intent(ACTION_LOG)
                .setClass(context, HabitWidgetProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val views = RemoteViews(context.packageName, R.layout.widget_habit).apply {
                setImageViewBitmap(R.id.widget_icon, IconFactory.circleBitmap(context, habit, iconSize(context)))
                setTextViewText(R.id.widget_label, habit.name)
                setContentDescription(R.id.widget_root, context.getString(R.string.widget_log_description, habit.name))
                setDisplayedChild(R.id.widget_flipper, 0)
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(context, widgetId, tap, flags))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun doneBitmap(context: Context, habit: Habit): Bitmap {
            val size = iconSize(context)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            HabitIconDrawable(context.getDrawable(R.drawable.ic_ui_check), null, habit.color).apply {
                setBounds(0, 0, size, size)
                draw(Canvas(bitmap))
            }
            return bitmap
        }

        private fun iconSize(context: Context) = (48 * context.resources.displayMetrics.density).toInt()
    }
}
