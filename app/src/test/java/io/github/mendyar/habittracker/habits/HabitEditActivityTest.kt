package io.github.mendyar.habittracker.habits

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.ui.Async
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class HabitEditActivityTest {

    private lateinit var context: Context
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        repository = HabitRepository.get(context)
        repository.ensureFirstRun()
    }

    @After
    fun tearDown() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
    }

    private fun open(habitId: Long?) = Robolectric.buildActivity(
        HabitEditActivity::class.java,
        HabitEditActivity.intent(context, habitId),
    ).setup().get()

    private fun HabitEditActivity.awaitLoaded(name: String) {
        awaitCondition { findViewById<EditText>(R.id.edit_name).text.toString() == name }
    }

    @Test
    fun theAppIconSwitchIsOnAndChangeableForTheAppIconHabit() {
        val habit = repository.defaultHabit()!!
        val activity = open(habit.id)
        activity.awaitLoaded(habit.name)
        val switch = activity.findViewById<Switch>(R.id.edit_main_icon)
        assertTrue(switch.isChecked)
        assertTrue(switch.isEnabled)
    }

    @Test
    fun switchingTheAppIconOffLeavesNoAppIconHabit() {
        val habit = repository.defaultHabit()!!
        val activity = open(habit.id)
        activity.awaitLoaded(habit.name)
        activity.findViewById<Switch>(R.id.edit_main_icon).isChecked = false
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.settings.defaultHabitId == -1L }
    }

    @Test
    fun switchingItOnForAnotherHabitMovesTheAppIcon() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val activity = open(water.id)
        activity.awaitLoaded("Water")
        val switch = activity.findViewById<Switch>(R.id.edit_main_icon)
        assertFalse(switch.isChecked)
        switch.isChecked = true
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.settings.defaultHabitId == water.id }
    }

    @Test
    fun aNewHabitCanTakeTheAppIcon() {
        repository.deleteHabit(repository.defaultHabit()!!.id)
        val activity = open(null)
        activity.findViewById<EditText>(R.id.edit_name).setText("Walk")
        activity.findViewById<Switch>(R.id.edit_main_icon).isChecked = true
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.defaultHabit()?.name == "Walk" }
    }

    @Test
    fun clearAllEntriesAsksFirstThenKeepsTheHabit() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repeat(4) { repository.log(water.id) }
        val activity = open(water.id)
        activity.awaitLoaded("Water")

        activity.findViewById<TextView>(R.id.edit_clear).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertEquals(4, repository.timestamps(water.id).size) // nothing happens before confirming
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        awaitCondition { repository.timestamps(water.id).isEmpty() }
        assertEquals("Water", repository.habit(water.id)?.name)
    }

    @Test
    fun cancellingClearAllEntriesKeepsThem() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repeat(2) { repository.log(water.id) }
        val activity = open(water.id)
        activity.awaitLoaded("Water")
        activity.findViewById<TextView>(R.id.edit_clear).performClick()
        (ShadowAlertDialog.getLatestAlertDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        Thread.sleep(200)
        assertEquals(2, repository.timestamps(water.id).size)
    }

    @Test
    fun headingsUseTitleCase() {
        assertEquals("Edit Habit", context.getString(R.string.edit_habit))
        assertEquals("New Habit", context.getString(R.string.new_habit))
        assertEquals("All Habits", context.getString(R.string.shortcut_habits_long))
        assertEquals("Daily Statistics", context.getString(R.string.stats_title_day))
        assertEquals("Clear All Entries", context.getString(R.string.clear_entries))
    }
}
