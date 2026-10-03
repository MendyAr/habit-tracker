package io.github.mendyar.habittracker.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitEditActivity
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.visible

/**
 * Asks which habit a widget should log. Only habits without a widget are offered,
 * since each habit has at most one. Opened by the launcher when a widget is placed
 * or reconfigured, and by tapping a widget whose habit is missing.
 */
class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Backing out must not leave a half-configured widget behind.
        setResult(RESULT_CANCELED, resultData())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContentView(R.layout.activity_widget_config)
        findViewById<View>(R.id.widget_new_habit).setOnClickListener {
            startActivityForResult(
                HabitEditActivity.intent(this, null).putExtra(HabitEditActivity.EXTRA_FOR_WIDGET, true),
                REQUEST_NEW_HABIT,
            )
        }
        val app = applicationContext
        val id = widgetId
        Async.load({ load(app, id) }) { result ->
            if (result == null) done() else render(result)
        }
    }

    /** Runs on the I/O thread; null when the widget was bound to a just-requested habit. */
    private fun load(app: android.content.Context, id: Int): List<Habit>? {
        val repository = HabitRepository.get(app)
        repository.ensureFirstRun()
        // Requested via "Add one-tap widget": the habit is already known, nothing to ask.
        if (repository.settings.widgetHabit(id) == -1L && HabitWidgetProvider.bindPending(app, id)) return null
        val taken = HabitWidgetProvider.widgetIds(app)
            .filter { it != id }
            .map { repository.settings.widgetHabit(it) }
            .toSet()
        return repository.habits().filter { it.id !in taken }
    }

    private fun render(habits: List<Habit>) {
        val list = findViewById<LinearLayout>(R.id.widget_habits)
        val inflater = LayoutInflater.from(this)
        findViewById<TextView>(R.id.widget_pick_title).setText(
            if (habits.isEmpty()) R.string.widget_none_free else R.string.widget_pick_title,
        )
        for (habit in habits) {
            val row = inflater.inflate(R.layout.item_habit, list, false)
            row.findViewById<ImageView>(R.id.habit_row_icon).setImageDrawable(IconFactory.drawable(this, habit))
            row.findViewById<TextView>(R.id.habit_row_name).text = habit.name
            row.findViewById<View>(R.id.habit_row_subtitle).visible = false
            row.findViewById<View>(R.id.habit_row_log).visible = false
            row.setOnClickListener { choose(habit.id) }
            list.addView(row)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val habitId = data?.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1) ?: -1
        if (requestCode == REQUEST_NEW_HABIT && resultCode == RESULT_OK && habitId != -1L) choose(habitId)
    }

    private fun choose(habitId: Long) {
        val app = applicationContext
        val id = widgetId
        Async.load({ HabitWidgetProvider.bind(app, id, habitId) }) { done() }
    }

    private fun done() {
        setResult(RESULT_OK, resultData())
        finish()
    }

    private fun resultData() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private companion object {
        const val REQUEST_NEW_HABIT = 1
    }
}
