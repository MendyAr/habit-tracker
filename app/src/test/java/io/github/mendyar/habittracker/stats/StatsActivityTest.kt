package io.github.mendyar.habittracker.stats

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class StatsActivityTest {

    private lateinit var context: Context
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        repository = HabitRepository.get(context)
    }

    @After
    fun tearDown() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
    }

    @Test
    fun showsTodaysCountAndStatistics() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        val now = System.currentTimeMillis()
        val day = 24 * 3_600_000L
        repository.log(habit.id, now)
        repository.log(habit.id, now - 1)
        repository.log(habit.id, now - 3 * day)

        val activity = Robolectric.buildActivity(StatsActivity::class.java, Shortcuts.statsIntent(context, habit.id))
            .setup().get()
        val count = activity.findViewById<TextView>(R.id.selected_count)
        awaitCondition { count.text.toString() == "2" }

        assertEquals("Smoking", activity.findViewById<TextView>(R.id.habit_name).text.toString())
        assertEquals(context.getString(R.string.today), activity.findViewById<TextView>(R.id.selected_label).text.toString())
        val total = activity.findViewById<android.view.View>(R.id.stat_total).findViewById<TextView>(R.id.stat_value)
        assertEquals("3", total.text.toString())
    }

    @Test
    fun choosingAPeriodIsRememberedForTheHabit() {
        val habit = repository.createHabit("Water", "water", null, HabitColors.DEFAULT)
        repository.log(habit.id)
        val activity = Robolectric.buildActivity(StatsActivity::class.java, Shortcuts.statsIntent(context, habit.id))
            .setup().get()
        val title = activity.findViewById<TextView>(R.id.stats_title)
        awaitCondition { title.text.isNotEmpty() }

        activity.findViewById<TextView>(R.id.period_month).performClick()
        assertEquals(context.getString(R.string.stats_title_month), title.text.toString())
        assertEquals(Period.MONTH, repository.settings.period(habit.id))
        assertTrue(activity.findViewById<TextView>(R.id.period_month).isSelected)
    }

    @Test
    fun opensTheDefaultHabitWithoutAnExtra() {
        repository.ensureFirstRun()
        val default = repository.defaultHabit()!!
        val activity = Robolectric.buildActivity(StatsActivity::class.java).setup().get()
        val name = activity.findViewById<TextView>(R.id.habit_name)
        awaitCondition { name.text.toString() == default.name }
    }

    @Test
    fun withoutAnyHabitItShowsTheHabitList() {
        repository.ensureFirstRun()
        repository.deleteHabit(repository.defaultHabit()!!.id)
        val activity = Robolectric.buildActivity(StatsActivity::class.java).setup().get()
        awaitCondition { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(HabitsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun longPressShortcutAlwaysReplacesTheAppsScreens() {
        // Otherwise an open editor would be brought back instead of the statistics.
        val flags = Shortcuts.statsShortcutIntent(context, 1).flags
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
