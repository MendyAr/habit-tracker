package io.github.mendyar.habittracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.launcher.PinResult
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.colorOf

/**
 * A resizable home-screen widget, at most one per habit. It is a translucent
 * neutral panel that blends with the wallpaper (on Android 12+ in the system's
 * wallpaper-derived colours), with the habit's icon as a disc in its colour; tapping it opens [io.github.mendyar.habittracker.log.LogActivity], which logs a
 * timestamp and shows its confirmation in the middle of the home screen in a
 * transparent window, exactly like a pinned habit icon. (Logging in the receiver
 * held each tap's broadcast open until the widget flipped back, which delayed the
 * next taps by seconds.)
 *
 * A widget whose habit is not chosen yet, or was deleted, says so and opens the
 * habit picker when tapped. It never silently switches to another habit.
 *
 * The nested subclasses are the same widget with a larger default size (see
 * [WidgetSize]); they are only used when the app adds a widget, and hidden from
 * the launcher's widget list.
 */
open class HabitWidgetProvider : AppWidgetProvider() {

    /** The widget added at 2 × 2 cells. */
    class Medium : HabitWidgetProvider()

    /** The widget added at 3 × 3 cells. */
    class Large : HabitWidgetProvider()

    /** The widget added at 4 × 4 cells. */
    class ExtraLarge : HabitWidgetProvider()

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending: PendingResult? = goAsync()
        val app = context.applicationContext
        Async.io {
            try {
                appWidgetIds.forEach { id ->
                    // A widget requested from the editor may appear before its callback arrives.
                    if (app.settingsHabit(id) == UNBOUND && bindPending(app, id)) notifyPinned(app.settingsHabit(id))
                    update(app, manager, id)
                }
            } finally {
                pending?.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val settings = HabitRepository.get(context).settings
        appWidgetIds.forEach { settings.removeWidget(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // A tap on a widget still drawn by a version that logged in place: redraw it,
            // so its next tap opens the confirmation window.
            ACTION_LOG -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (widgetId == -1) return
                val pending: PendingResult? = goAsync()
                val app = context.applicationContext
                Async.io {
                    try {
                        update(app, AppWidgetManager.getInstance(app), widgetId)
                    } finally {
                        pending?.finish()
                    }
                }
            }
            ACTION_PINNED -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val habitId = intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1)
                if (widgetId == -1 || habitId == -1L) return
                val pending: PendingResult? = goAsync()
                val app = context.applicationContext
                Async.io {
                    try {
                        // Usually already bound (and announced) by onUpdate, through the pending request.
                        if (app.settingsHabit(widgetId) == UNBOUND && bind(app, widgetId, habitId)) notifyPinned(habitId)
                    } finally {
                        pending?.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        /** The tap action of widgets drawn by versions up to 2.0. */
        internal const val ACTION_LOG = "io.github.mendyar.habittracker.action.WIDGET_LOG"
        internal const val ACTION_PINNED = "io.github.mendyar.habittracker.action.WIDGET_PINNED"
        private const val UNBOUND = -1L

        private fun Context.settingsHabit(widgetId: Int) = HabitRepository.get(this).settings.widgetHabit(widgetId)

        /**
         * Told on the main thread when a widget requested with [requestPin] has been
         * placed on the home screen and shows its habit.
         */
        @Volatile
        var onPinned: ((habitId: Long) -> Unit)? = null

        private fun notifyPinned(habitId: Long) {
            Async.main { onPinned?.invoke(habitId) }
        }

        /** Ids of the widgets currently on the home screen, of every size. */
        fun widgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
            return WidgetSize.entries.flatMap { size ->
                manager.getAppWidgetIds(ComponentName(context, size.provider)).asList()
            }.toIntArray()
        }

        /** Whether widgets can be added in a chosen [WidgetSize] (Android 9+). */
        fun sizesAvailable(context: Context): Boolean = context.resources.getBoolean(R.bool.sized_widgets)

        /** The widget showing [habitId], if any (there is at most one). */
        fun widgetFor(context: Context, habitId: Long): Int? {
            val settings = HabitRepository.get(context).settings
            return widgetIds(context).firstOrNull { settings.widgetHabit(it) == habitId }
        }

        /** Redraws the widget of [habitId], if it has one. Call on the [Async] I/O thread. */
        fun updateFor(context: Context, habitId: Long) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            widgetFor(context, habitId)?.let { update(context, manager, it) }
        }

        /** Redraws every widget after habits changed. Call on the [Async] I/O thread. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            widgetIds(context).forEach { update(context, manager, it) }
        }

        /**
         * Associates [widgetId] with [habitId] and redraws it. Refused (returning
         * false) when another widget already shows that habit. Call on the [Async]
         * I/O thread: binding and drawing must be serialised with onUpdate, or a view
         * computed before the binding could be published after it.
         */
        fun bind(context: Context, widgetId: Int, habitId: Long): Boolean {
            val existing = widgetFor(context, habitId)
            if (existing != null && existing != widgetId) return false
            HabitRepository.get(context).settings.setWidgetHabit(widgetId, habitId)
            update(context, AppWidgetManager.getInstance(context), widgetId)
            return true
        }

        /** Binds [widgetId] to a habit requested from the editor moments ago, if any. */
        fun bindPending(context: Context, widgetId: Int): Boolean {
            val habitId = HabitRepository.get(context).settings.takePendingWidget()
            if (habitId == UNBOUND || HabitRepository.get(context).habit(habitId) == null) return false
            return bind(context, widgetId, habitId)
        }

        /**
         * Asks the launcher to place a widget for [habit] (Android 8+) at [size],
         * unless the habit already has one. Call off the main thread.
         */
        fun requestPin(context: Context, habit: Habit, size: WidgetSize = WidgetSize.SMALL): PinResult {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return PinResult.UNSUPPORTED
            if (widgetFor(context, habit.id) != null) return PinResult.ALREADY_EXISTS
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return PinResult.UNSUPPORTED
            if (!manager.isRequestPinAppWidgetSupported) return PinResult.UNSUPPORTED
            HabitRepository.get(context).settings.setPendingWidget(habit.id)
            val callback = Intent(ACTION_PINNED)
                .setClass(context, HabitWidgetProvider::class.java)
                .putExtra(Shortcuts.EXTRA_HABIT_ID, habit.id)
            // Mutable: the system adds the new widget id to this intent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, habit.id.toInt(), callback, flags)
            val provider = if (sizesAvailable(context)) size.provider else HabitWidgetProvider::class.java
            val requested = manager.requestPinAppWidget(
                ComponentName(context, provider),
                Bundle(),
                pending,
            )
            return if (requested) PinResult.REQUESTED else PinResult.UNSUPPORTED
        }

        /** Builds the full widget for its current state. */
        internal fun views(context: Context, widgetId: Int): RemoteViews {
            val repository = HabitRepository.get(context)
            val habitId = repository.settings.widgetHabit(widgetId)
            val habit = repository.habit(habitId)
            val views = RemoteViews(context.packageName, R.layout.widget_habit)
            if (habit != null) {
                views.setImageViewBitmap(R.id.widget_icon, iconBitmap(context, habit))
                views.setTextViewText(R.id.widget_label, habit.name)
                views.setContentDescription(R.id.widget_root, context.getString(R.string.widget_log_description, habit.name))
                showSession(context, views, habit)
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, widgetId, tapIntent(context, habit.id), immutable()))
            } else {
                // Not chosen yet, or its habit was deleted: say so, and let a tap pick a habit.
                val label = if (habitId == UNBOUND) R.string.widget_choose else R.string.widget_deleted
                views.setImageViewBitmap(R.id.widget_icon, glyphBitmap(context, context.getDrawable(R.drawable.ic_ui_add), context.colorOf(R.color.widget_text)))
                views.setTextViewText(R.id.widget_label, context.getString(label))
                views.setContentDescription(R.id.widget_root, context.getString(label))
                val pick = Intent(context, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, widgetId, pick, immutable()))
            }
            return views
        }

        /** Duration habits: the stopwatch badge, and a live timer while a session runs. */
        private fun showSession(context: Context, views: RemoteViews, habit: Habit) {
            val duration = habit.type == HabitType.DURATION
            views.setViewVisibility(R.id.widget_badge, if (duration) View.VISIBLE else View.GONE)
            if (duration) views.setImageViewBitmap(R.id.widget_badge, glyphBitmap(context, context.getDrawable(R.drawable.ic_habit_timer), habit.color))
            val session = if (duration) HabitRepository.get(context).running(habit.id) else null
            if (session != null) {
                val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - session.timestamp)
                views.setChronometer(R.id.widget_timer, base, null, true)
                views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
                // The timer takes the name's place, so it fits even a 1 × 1 widget.
                views.setViewVisibility(R.id.widget_label, View.GONE)
            } else {
                views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.widget_timer, View.GONE)
                views.setViewVisibility(R.id.widget_label, View.VISIBLE)
            }
        }

        /**
         * What a tap on a habit's widget starts: the same transparent logging window as a
         * habit icon. The launcher adds the widget's position (source bounds), so taps on
         * that spot during the confirmation count as taps of the widget.
         */
        internal fun tapIntent(context: Context, habitId: Long): Intent =
            Shortcuts.logIntent(context, habitId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            manager.updateAppWidget(widgetId, views(context, widgetId))
        }

        private fun immutable() = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        /** The habit's icon as on the home screen: a disc in its colour with the glyph, or its own image. */
        internal fun iconBitmap(context: Context, habit: Habit): Bitmap =
            IconFactory.circleBitmap(context, habit, (ICON_DP * context.resources.displayMetrics.density).toInt())

        private fun glyphBitmap(context: Context, glyph: Drawable?, color: Int): Bitmap {
            glyph?.mutate()?.setTint(color)
            return render(context, glyph)
        }

        private fun render(context: Context, drawable: Drawable?): Bitmap {
            val size = (ICON_DP * context.resources.displayMetrics.density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable?.setBounds(0, 0, size, size)
            drawable?.draw(Canvas(bitmap))
            return bitmap
        }

        /** Largest icon size; smaller widgets scale it down (centerInside). */
        private const val ICON_DP = 72
    }
}
