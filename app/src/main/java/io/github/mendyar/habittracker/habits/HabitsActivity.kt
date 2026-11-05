package io.github.mendyar.habittracker.habits

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.onboarding.Onboarding
import io.github.mendyar.habittracker.onboarding.OnboardingActivity
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.Formats
import io.github.mendyar.habittracker.ui.visible

/** Lists every habit with its current count; habits can be logged, opened or added here. */
class HabitsActivity : Activity() {

    private lateinit var repository: HabitRepository
    private lateinit var formats: Formats
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_habits)
        findViewById<View>(R.id.habits_how_to).setOnClickListener {
            startActivity(OnboardingActivity.intent(this, Onboarding.Mode.TUTORIAL))
        }
        repository = HabitRepository.get(this)
        formats = Formats(this)
        list = findViewById(R.id.habits_list)

        findViewById<View>(R.id.back_button).apply {
            // Opened straight from a launcher shortcut there is nothing to go back to.
            visible = !isTaskRoot
            setOnClickListener { finish() }
        }
        findViewById<View>(R.id.add_habit_button).setOnClickListener {
            startActivity(HabitEditActivity.intent(this, null))
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private class Row(val habit: Habit, val subtitle: String)

    private fun load() {
        Async.load({ rows() }) { rows ->
            if (!isFinishing) render(rows)
        }
    }

    /** Runs on the I/O thread. */
    private fun rows(): List<Row> {
        val now = System.currentTimeMillis()
        val math = PeriodMath()
        return repository.habits().map { habit ->
            Row(habit, subtitle(habit, now, math))
        }
    }

    private fun subtitle(habit: Habit, now: Long, math: PeriodMath): String {
        if (habit.type == HabitType.DURATION) {
            repository.running(habit.id)?.let { return getString(R.string.running_since, formats.time(it.timestamp)) }
        }
        val period = repository.settings.period(habit.id)
        val count = repository.count(habit.id, math.startOf(period, now), math.endOf(period, now))
        return formats.countPhrase(period, count)
    }

    private fun render(rows: List<Row>) {
        list.removeAllViews()
        list.visible = rows.isNotEmpty()
        findViewById<View>(R.id.habits_empty).visible = rows.isEmpty()
        val inflater = LayoutInflater.from(this)
        for (row in rows) {
            val view = inflater.inflate(R.layout.item_habit, list, false)
            val subtitle = view.findViewById<TextView>(R.id.habit_row_subtitle)
            view.findViewById<ImageView>(R.id.habit_row_icon).setImageDrawable(IconFactory.drawable(this, row.habit))
            view.findViewById<TextView>(R.id.habit_row_name).text = row.habit.name
            subtitle.text = row.subtitle
            view.setOnClickListener { startActivity(Shortcuts.statsIntent(this, row.habit.id)) }
            view.findViewById<ImageButton>(R.id.habit_row_log).apply {
                contentDescription = getString(R.string.log_habit, row.habit.name)
                setOnClickListener { log(row.habit) }
            }
            list.addView(view)
        }
    }

    /**
     * Logs [habit] from its + button through the same window as its widget: the
     * confirmation, "Log Again?" right after an entry, the session rules of duration
     * habits and the trigger suggestion. The list refreshes when it closes.
     */
    private fun log(habit: Habit) {
        startActivity(Shortcuts.logIntent(this, habit.id))
    }
}
