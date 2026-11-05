package io.github.mendyar.habittracker.habits

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.widget.HabitWidgetProvider
import io.github.mendyar.habittracker.widget.WidgetSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.FileOutputStream

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
    fun aNewHabitIsSavedWithoutAnyHomeScreenSection() {
        val activity = open(null)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.edit_home_section).visibility)
        activity.findViewById<EditText>(R.id.edit_name).setText("Walk")
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.habits().any { it.name == "Walk" } }
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
        assertEquals("New Habit", context.getString(R.string.shortcut_new_habit))
        assertEquals("Daily Statistics", context.getString(R.string.stats_title_day))
        assertEquals("Clear All Entries", context.getString(R.string.clear_entries))
    }

    @Test
    fun habitsGoOnTheHomeScreenAsWidgetsOnly() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val activity = open(water.id)
        activity.awaitLoaded("Water")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.edit_add_widget).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.edit_widget_hint).visibility)
        // No way left to create a home-screen icon.
        assertTrue(findByText(activity.window.decorView, "Icon to Home Screen") == null)
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [25])
    fun beforeAndroid8TheEditorSaysHowToAddAWidgetFromTheLauncher() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val activity = open(water.id)
        activity.awaitLoaded("Water")
        assertEquals(View.GONE, activity.findViewById<View>(R.id.edit_add_widget).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.edit_widget_hint).visibility)
    }

    @Test
    fun afterCreatingAHabitTheEditorOffersItsWidget() {
        val activity = open(null)
        activity.findViewById<EditText>(R.id.edit_name).setText("Walk")
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertTrue(findByText(dialog.window!!.decorView, context.getString(R.string.add_widget_title)) != null)

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle() // dialog clicks are delivered through the looper
        val walk = repository.habits().first { it.name == "Walk" }
        val next = shadowOf(activity).nextStartedActivity
        assertEquals(HabitEditActivity::class.java.name, next.component?.className)
        assertEquals(walk.id, next.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1))
        assertTrue(next.getBooleanExtra(HabitEditActivity.EXTRA_ADD_WIDGET, false))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun addingAWidgetAgainSaysItIsAlreadyOnTheHomeScreen() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = shadowOf(AppWidgetManager.getInstance(context)).createWidget(HabitWidgetProvider::class.java, R.layout.widget_habit)
        Async.awaitIdle()
        Async.io { HabitWidgetProvider.bind(context, id, water.id) }
        Async.awaitIdle()
        val activity = open(water.id)
        activity.awaitLoaded("Water")

        activity.findViewById<TextView>(R.id.edit_add_widget).performClick()
        awaitCondition { ShadowToast.getTextOfLatestToast() == context.getString(R.string.widget_exists, "Water") }
    }

    @Test
    fun aWidgetIsAddedInTheChosenSizeThenTheHomeScreenShowsIt() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val activity = open(water.id)
        activity.awaitLoaded("Water")

        activity.findViewById<TextView>(R.id.edit_add_widget).performClick()
        awaitCondition { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val option = findByDescription(dialog.window!!.decorView, context.getString(R.string.widget_size_description, 2, 2))
        option!!.performClick()

        // Robolectric's launcher places the widget at once and reports back, like
        // "Add to home screen" in the launcher's dialog: the app then shows the home screen.
        awaitCondition { activity.isFinishing }
        val home = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, home.action)
        assertTrue(home.hasCategory(Intent.CATEGORY_HOME))
        assertEquals(context.getString(R.string.widget_added), ShadowToast.getTextOfLatestToast())

        // In the chosen size, showing this habit.
        val id = HabitWidgetProvider.widgetFor(context, water.id)!!
        val medium = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WidgetSize.MEDIUM.provider))
        assertTrue(id in medium)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun yourOwnImageIsALabelledOptionThatBecomesTheIcon() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val activity = open(water.id)
        activity.awaitLoaded("Water")
        val own = activity.findViewById<TextView>(R.id.edit_own_image)
        val remove = activity.findViewById<TextView>(R.id.edit_own_image_remove)
        assertEquals(context.getString(R.string.upload_icon), own.text.toString())
        assertEquals(View.VISIBLE, own.visibility)
        assertEquals(View.GONE, remove.visibility)

        own.performClick()
        val pick = shadowOf(activity).nextStartedActivityForResult
        assertTrue(pick.intent.action in setOf(MediaStore.ACTION_PICK_IMAGES, Intent.ACTION_GET_CONTENT))

        // The user picks a photo.
        val photo = File(context.cacheDir, "photo.png")
        FileOutputStream(photo).use {
            Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        shadowOf(activity).receiveResult(pick.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(photo)))
        awaitCondition { own.text.toString() == context.getString(R.string.change_image) }
        assertEquals(View.VISIBLE, remove.visibility)

        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.habit(water.id)?.customIcon != null }
        assertTrue(File(repository.habit(water.id)!!.customIcon!!).exists())
    }

    @Test
    fun removingYourImageGoesBackToTheBuiltInIcon() {
        val image = File(context.filesDir, "own.png").apply { writeBytes(ByteArray(1)) }
        val water = repository.createHabit("Water", "water", image.path, HabitColors.ALL[1])
        val activity = open(water.id)
        activity.awaitLoaded("Water")
        val own = activity.findViewById<TextView>(R.id.edit_own_image)
        val remove = activity.findViewById<TextView>(R.id.edit_own_image_remove)
        assertEquals(context.getString(R.string.change_image), own.text.toString())

        remove.performClick()
        assertEquals(context.getString(R.string.upload_icon), own.text.toString())
        assertEquals(View.GONE, remove.visibility)
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.habit(water.id)?.customIcon == null }
        assertEquals("water", repository.habit(water.id)?.icon)
    }

    @Test
    fun aNewHabitCanChooseItsStatisticsBeforeSaving() {
        val activity = open(null)
        activity.findViewById<EditText>(R.id.edit_name).setText("Breathing")
        activity.findViewById<TextView>(R.id.edit_statistics).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        // Resetting keeps the default; saving the habit stores nothing custom.
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.habits().any { it.name == "Breathing" } }
        val id = repository.habits().first { it.name == "Breathing" }.id
        Async.awaitIdle()
        assertEquals(null, repository.settings.statsConfig(id))
    }

    @Test
    fun aNewHabitCanBeADurationTracker() {
        val activity = open(null)
        activity.findViewById<EditText>(R.id.edit_name).setText("Box breathing")
        activity.findViewById<android.widget.RadioButton>(R.id.edit_type_duration).isChecked = true
        activity.findViewById<TextView>(R.id.save_button).performClick()
        awaitCondition { repository.habits().any { it.name == "Box breathing" } }
        assertEquals(
            io.github.mendyar.habittracker.data.HabitType.DURATION,
            repository.habits().first { it.name == "Box breathing" }.type,
        )
    }

    @Test
    fun countHabitsCanTurnOffAndEditTheirTriggerSuggestion() {
        val smoking = repository.createHabit("Smoking", "smoking", null, HabitColors.ALL[5])
        val activity = open(smoking.id)
        activity.awaitLoaded("Smoking")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.edit_triggers_section).visibility)
        val suggest = activity.findViewById<android.widget.Switch>(R.id.edit_suggest_triggers)
        assertTrue(suggest.isChecked)
        suggest.isChecked = false
        assertFalse(repository.settings.suggestTriggers(smoking.id))
        suggest.isChecked = true

        activity.findViewById<View>(R.id.edit_triggers).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        // Remove the first preset, then add one.
        findByDescription(dialog.window!!.decorView, context.getString(R.string.trigger_remove, "Craving"))!!.performClick()
        findEditText(dialog.window!!.decorView)!!.setText("Phone Call")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(
            io.github.mendyar.habittracker.data.Triggers.PRESETS.drop(1) + "Phone Call",
            repository.settings.triggers(smoking.id),
        )
    }

    @Test
    fun durationHabitsHaveNoTriggers() {
        val breathing = repository.createHabit("Box breathing", "box_breathing", null, HabitColors.ALL[3], io.github.mendyar.habittracker.data.HabitType.DURATION)
        val activity = open(breathing.id)
        activity.awaitLoaded("Box breathing")
        assertEquals(View.GONE, activity.findViewById<View>(R.id.edit_triggers_section).visibility)
    }

    private fun findEditText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findEditText(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun findByText(view: View, text: String): View? {
        if (view is TextView && view.visibility == View.VISIBLE && view.text.contains(text)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findByText(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun findByDescription(view: View, description: String): View? {
        if (view.contentDescription == description) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findByDescription(view.getChildAt(i), description)?.let { return it }
        }
        return null
    }
}
