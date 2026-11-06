package io.github.mendyar.habittracker.stats

import android.content.Context
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.Formats
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
import org.robolectric.shadows.ShadowAlertDialog

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

        val activity = open(habit.id)
        val count = activity.findViewById<TextView>(R.id.selected_count)
        awaitCondition { count.text.toString() == "2" }

        assertEquals("Smoking", activity.findViewById<TextView>(R.id.habit_name).text.toString())
        assertEquals(context.getString(R.string.today), activity.findViewById<TextView>(R.id.selected_label).text.toString())
        // Default statistics: the three finished days had 1, 0 and 0 entries (today is left out).
        assertEquals("0", statValue(activity, R.string.stat_median))
        assertEquals("0.5", statValue(activity, R.string.stat_iqr))
        assertEquals("Middle half of days: 0–0.5", statNote(activity, R.string.stat_iqr))
        assertNull(statValue(activity, R.string.stat_range)) // optional, not shown by default
        assertNull(statValue(activity, R.string.stat_total))
    }

    @Test
    fun theDefaultsAreTheResearchSetGroupedTypicalThenSpread() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        repository.log(habit.id, System.currentTimeMillis() - 24 * 3_600_000L)
        val activity = open(habit.id)
        awaitCondition { statValue(activity, R.string.stat_median) != null }
        val shown = statLabels(activity)
        assertEquals(
            listOf("Median", "Trimmed mean", "Mean", "Mean without flagged days", "IQR (middle half)", "Standard deviation"),
            shown,
        )
        // Group headings separate them.
        val texts = allTexts(activity.findViewById(R.id.stats_grid))
        assertTrue(texts.indexOf(context.getString(R.string.group_typical)) < texts.indexOf("Median"))
        assertTrue(texts.indexOf(context.getString(R.string.group_spread)) in texts.indexOf("Mean without flagged days")..texts.indexOf("IQR (middle half)"))

        // The cards of the report; the time-of-day curve is optional.
        for (card in listOf(R.id.history_card, R.id.hours_card, R.id.first_card, R.id.weekdays_card, R.id.triggers_card, R.id.entries_card)) {
            assertEquals(View.VISIBLE, activity.findViewById<View>(card).visibility)
        }
        for (card in listOf(R.id.time_card, R.id.trend_card, R.id.durations_card)) {
            assertEquals(View.GONE, activity.findViewById<View>(card).visibility)
        }
    }

    @Test
    fun durationHabitsShowTheirSessionTrend() {
        val habit = repository.createHabit("Box breathing", "box_breathing", null, HabitColors.DEFAULT, HabitType.DURATION)
        val now = System.currentTimeMillis()
        listOf(8L, 6L, 4L).forEachIndexed { i, minutes -> repository.addSession(habit.id, now - (3 - i) * 3_600_000L, minutes * 60_000) }
        val activity = open(habit.id)
        awaitCondition { statValue(activity, R.string.stat_dur_median) == "6:00" }
        assertEquals(listOf("Median time", "Mean time", "IQR (middle half)", "Under target"), statLabels(activity))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.trend_card).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.history_card).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.hours_card).visibility)
        // The newest session, with the median of the sessions so far.
        assertEquals("${Formats(context).shortDate(now - 3_600_000L)} · 4:00 · median of last 7: 6:00", activity.findViewById<TextView>(R.id.trend_readout).text.toString())
    }

    @Test
    fun customizedStatisticsAndCardsAreShownPerHabit() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        val now = System.currentTimeMillis()
        repository.log(habit.id, now - 2 * 24 * 3_600_000L)
        repository.settings.setStatsConfig(
            habit.id,
            StatsConfig(stats = listOf(StatKind.TOTAL, StatKind.MEAN), cards = setOf(StatsCard.HISTORY, StatsCard.WHEN)).encode(),
        )
        val activity = open(habit.id)
        awaitCondition { statValue(activity, R.string.stat_total) == "1" }
        assertNull(statValue(activity, R.string.stat_median))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.entries_card).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.hours_card).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.history_card).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.time_card).visibility)
    }

    @Test
    fun tappingAStatisticExplainsItForThePeriod() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        repository.log(habit.id, System.currentTimeMillis() - 24 * 3_600_000L)
        val activity = open(habit.id)
        awaitCondition { statValue(activity, R.string.stat_median) != null }
        statRow(activity, R.string.stat_median)!!.performClick()
        assertEquals(
            "Your typical day: half of your days are below it, half above. One unusual day doesn't move it.",
            latestMessage(),
        )
    }

    @Test
    fun thePeriodIsChosenInCustomizeOnly() {
        val habit = repository.createHabit("Water", "water", null, HabitColors.DEFAULT)
        repository.log(habit.id, System.currentTimeMillis() - 40 * 24 * 3_600_000L)
        val activity = open(habit.id)
        val title = activity.findViewById<TextView>(R.id.stats_title)
        awaitCondition { title.text.isNotEmpty() }
        // No Day / Week / Month / Year tabs on the screen.
        assertTrue(allTexts(activity.window.decorView).none { it == context.getString(R.string.period_month) })

        activity.findViewById<View>(R.id.stats_customize).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        (findText(dialog.window!!.decorView, context.getString(R.string.period_month)) as RadioButton).isChecked = true
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle() // dialog clicks are delivered through the looper

        assertEquals(context.getString(R.string.stats_title_month), title.text.toString())
        assertEquals(context.getString(R.string.history_month), activity.findViewById<TextView>(R.id.history_title).text.toString())
        assertEquals(Period.MONTH, repository.settings.period(habit.id))
        // Explanations now speak of months.
        statRow(activity, R.string.stat_median)!!.performClick()
        assertTrue(latestMessage(), latestMessage().startsWith("Your typical month: half of your months"))
    }

    @Test
    fun chartsAreExplainedFromTheirTitlesOnly() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        val now = System.currentTimeMillis()
        listOf(3, 10, 17, 40).forEach { repository.log(habit.id, now - it * 24 * 3_600_000L) }
        val activity = open(habit.id)
        awaitCondition { activity.findViewById<TextView>(R.id.hours_readout).text.isNotEmpty() }
        ShadowAlertDialog.reset()

        // Touching a chart selects; it doesn't open an explanation.
        val chart = activity.findViewById<View>(R.id.hours_chart)
        chart.layout(0, 0, 600, 300)
        val time = android.os.SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach {
            chart.dispatchTouchEvent(MotionEvent.obtain(time, time, it, 300f, 150f, 0))
        }
        assertNull(ShadowAlertDialog.getLatestAlertDialog())

        activity.findViewById<View>(R.id.hours_title).performClick()
        assertEquals(context.getString(R.string.help_hours), latestMessage())
    }

    @Test
    fun whatSetsItOffShowsTheShareOfEachTrigger() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        val now = System.currentTimeMillis()
        val tags = listOf("Coffee", "Coffee", "Coffee", "Stress", null)
        tags.forEachIndexed { i, tag ->
            val id = repository.log(habit.id, now - (i + 1) * 3_600_000L)
            if (tag != null) repository.setTrigger(id, tag)
        }
        val activity = open(habit.id)
        val list = activity.findViewById<ViewGroup>(R.id.triggers_list)
        awaitCondition { list.childCount == 2 }
        val formats = Formats(context)
        assertEquals("Coffee: ${formats.percent(0.75)}", list.getChildAt(0).contentDescription)
        assertEquals("Stress: ${formats.percent(0.25)}", list.getChildAt(1).contentDescription)
        assertEquals(
            context.getString(R.string.triggers_caption, "4", "5"),
            activity.findViewById<TextView>(R.id.triggers_caption).text.toString(),
        )
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.triggers_card).visibility)
    }

    private fun open(habitId: Long): StatsActivity =
        Robolectric.buildActivity(StatsActivity::class.java, Shortcuts.statsIntent(context, habitId)).setup().get()

    private fun latestMessage(): String = shadowOf(ShadowAlertDialog.getLatestAlertDialog()).message.toString()

    private fun labelText(label: Int) = context.getString(label, "day", "days")

    private fun statRow(activity: StatsActivity, label: Int): View? {
        val grid = activity.findViewById<ViewGroup>(R.id.stats_grid)
        for (i in 0 until grid.childCount) {
            val row = grid.getChildAt(i)
            if (row.findViewById<TextView>(R.id.stat_label)?.text?.toString() == labelText(label)) return row
        }
        return null
    }

    private fun statLabels(activity: StatsActivity): List<String> {
        val grid = activity.findViewById<ViewGroup>(R.id.stats_grid)
        return (0 until grid.childCount).mapNotNull { grid.getChildAt(it).findViewById<TextView>(R.id.stat_label)?.text?.toString() }
    }

    private fun statValue(activity: StatsActivity, label: Int): String? =
        statRow(activity, label)?.findViewById<TextView>(R.id.stat_value)?.text?.toString()

    private fun statNote(activity: StatsActivity, label: Int): String? =
        statRow(activity, label)?.findViewById<TextView>(R.id.stat_note)?.text?.toString()

    private fun allTexts(view: View): List<String> = when {
        view is TextView && view.visibility == View.VISIBLE -> listOf(view.text.toString())
        view is ViewGroup && view.visibility == View.VISIBLE -> (0 until view.childCount).flatMap { allTexts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun findText(view: View, text: String): View? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findText(view.getChildAt(i), text)?.let { return it }
        return null
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
}
