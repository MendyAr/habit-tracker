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

/** Asks which habit a newly placed (or reconfigured) widget should log. */
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
        val repository = HabitRepository.get(this)
        Async.load({
            repository.defaultHabit()
            repository.habits()
        }, ::render)
    }

    private fun render(habits: List<Habit>) {
        val list = findViewById<LinearLayout>(R.id.widget_habits)
        val inflater = LayoutInflater.from(this)
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
        Async.load({ HabitWidgetProvider.bind(app, widgetId, habitId) }) {
            setResult(RESULT_OK, resultData())
            finish()
        }
    }

    private fun resultData() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private companion object {
        const val REQUEST_NEW_HABIT = 1
    }
}
