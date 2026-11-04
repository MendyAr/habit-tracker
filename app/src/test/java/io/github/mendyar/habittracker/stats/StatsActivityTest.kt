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
import org.junit.Assert.assertNull
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
        // Default statistics: the three finished days had 1, 0 and 0 entries (today is left out).
        assertEquals("0", statValue(activity, R.string.stat_median))
        assertEquals("0–1", statValue(activity, R.string.stat_range))
        assertNull(statValue(activity, R.string.stat_total)) // optional, not shown by default
    }

    @Test
    fun customizedStatisticsAndCardsAreShownPerHabit() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        val now = System.currentTimeMillis()
        repository.log(habit.id, now - 2 * 24 * 3_600_000L)
        repository.settings.setStatsConfig(
            habit.id,
            StatsConfig(stats = listOf(StatKind.TOTAL, StatKind.MEAN), hiddenCards = setOf(StatsCard.ENTRIES)).encode(),
        )
        val activity = Robolectric.buildActivity(StatsActivity::class.java, Shortcuts.statsIntent(context, habit.id))
            .setup().get()
        awaitCondition { statValue(activity, R.string.stat_total) == "1" }
        assertNull(statValue(activity, R.string.stat_median))
        assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.entries_card).visibility)
        assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.history_card).visibility)
    }

    @Test
    fun tappingAStatisticExplainsIt() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        repository.log(habit.id, System.currentTimeMillis() - 24 * 3_600_000L)
        val activity = Robolectric.buildActivity(StatsActivity::class.java, Shortcuts.statsIntent(context, habit.id))
            .setup().get()
        awaitCondition { statValue(activity, R.string.stat_median) != null }
        statCell(activity, R.string.stat_median)!!.performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(
            context.getString(R.string.help_median),
            shadowOf(dialog).message.toString(),
        )
    }

    private fun statCell(activity: StatsActivity, label: Int): android.view.View? {
        val grid = activity.findViewById<android.view.ViewGroup>(R.id.stats_grid)
        for (r in 0 until grid.childCount) {
            val row = grid.getChildAt(r) as android.view.ViewGroup
            for (c in 0 until row.childCount) {
                val cell = row.getChildAt(c)
                val text = cell.findViewById<TextView>(R.id.stat_label)?.text?.toString() ?: continue
                if (text == context.getString(label)) return cell
            }
        }
        return null
    }

    private fun statValue(activity: StatsActivity, label: Int): String? =
        statCell(activity, label)?.findViewById<TextView>(R.id.stat_value)?.text?.toString()

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
    fun opensAHabitWithoutAnExtra() {
        val default = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val activity = Robolectric.buildActivity(StatsActivity::class.java).setup().get()
        val name = activity.findViewById<TextView>(R.id.habit_name)
        awaitCondition { name.text.toString() == default.name }
    }

    @Test
    fun withoutAnyHabitItShowsTheHabitList() {
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

    @Test
    fun periodTabsChangeTheWhenChart() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        val day = 24 * 3_600_000L
        val now = System.currentTimeMillis()
        listOf(3, 10, 17, 40).forEach { repository.log(habit.id, now - it * day) }
        val activity = Robolectric.buildActivity(StatsActivity::class.java, Shortcuts.statsIntent(context, habit.id))
            .setup().get()
        val title = activity.findViewById<TextView>(R.id.time_title)
        val readout = activity.findViewById<TextView>(R.id.time_readout)
        awaitCondition { activity.findViewById<TextView>(R.id.habit_name).text.toString() == "Smoking" }
        assertEquals(context.getString(R.string.time_of_day), title.text.toString())

        val tabs = mapOf(
            R.id.period_week to R.string.day_of_week,
            R.id.period_month to R.string.day_of_month,
            R.id.period_year to R.string.month_of_year,
        )
        for ((tab, expected) in tabs) {
            activity.findViewById<TextView>(tab).performClick()
            assertEquals(context.getString(expected), title.text.toString())
            assertTrue(readout.text.toString(), readout.text.startsWith("Most likely: "))
        }
        activity.findViewById<TextView>(R.id.period_day).performClick()
        assertEquals(context.getString(R.string.time_of_day), title.text.toString())
    }
}
