package io.github.mendyar.habittracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitEditActivity
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.log.LogActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Random

/**
 * Opens every screen with realistic data, checks it renders, and saves a
 * screenshot of each to the app's external files dir (CI collects them).
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        HabitRepository.resetForTests()
        context.deleteDatabase("habits.db")
        repository = HabitRepository.get(context)
        repository.settings.hintShown = true
    }

    /** Two months of a habit that is slowly being cut down, plus two more habits. */
    private fun seed(): Long {
        val smoking = repository.defaultHabit().copy(name = "Smoking", icon = "smoking", color = HabitColors.ALL[5])
        repository.updateHabit(smoking)
        val random = Random(7)
        val now = System.currentTimeMillis()
        val day = 24 * 3_600_000L
        for (d in 60 downTo 1) {
            val perDay = 4 + d / 6 + random.nextInt(3)
            repeat(perDay) { repository.log(smoking.id, now - d * day + random.nextInt(14) * 3_600_000L) }
        }
        repeat(5) { repository.log(smoking.id, now - it * 1_800_000L) }
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repeat(6) { repository.log(water.id, now - it * 3_000_000L) }
        repository.createHabit("Meditation", "meditation", null, HabitColors.ALL[3])
        return smoking.id
    }

    @Test
    fun statisticsScreen() {
        val id = seed()
        val math = PeriodMath()
        val now = System.currentTimeMillis()
        val today = repository.count(id, math.startOf(Period.DAY, now), math.endOf(Period.DAY, now))
        launch(Shortcuts.statsIntent(context, id))
        val activity = waitForActivity()
        waitUntil { activity.findViewById<TextView>(R.id.selected_count).text.toString() == today.toString() }
        screenshot("1_statistics_day")

        instrumentation.runOnMainSync { activity.findViewById<TextView>(R.id.period_week).performClick() }
        instrumentation.waitForIdleSync()
        assertEquals(Period.WEEK, repository.settings.period(id))
        screenshot("2_statistics_week")
        activity.finish()
    }

    @Test
    fun habitsScreen() {
        seed()
        launch(Intent(context, HabitsActivity::class.java))
        val activity = waitForActivity()
        waitUntil { (activity.findViewById<android.view.ViewGroup>(R.id.habits_list)).childCount == 3 }
        screenshot("3_habits")
        activity.finish()
    }

    @Test
    fun editorScreen() {
        val id = seed()
        launch(HabitEditActivity.intent(context, id))
        val activity = waitForActivity()
        waitUntil { activity.findViewById<TextView>(R.id.edit_name).text.toString() == "Smoking" }
        screenshot("4_edit_habit")
        activity.finish()
    }

    @Test
    fun logConfirmationOverHomeScreen() {
        seed()
        device.pressHome()
        device.waitForIdle()
        val w = device.displayWidth
        val h = device.displayHeight
        val size = w / 6
        val intent = Intent(Intent.ACTION_MAIN)
            .setClass(context, LogActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.sourceBounds = Rect(w / 2 - size / 2, h * 2 / 3, w / 2 + size / 2, h * 2 / 3 + size)
        context.startActivity(intent)
        Thread.sleep(550)
        screenshot("5_log_animation")
        Thread.sleep(2_000)
        assertTrue(repository.timestamps(repository.defaultHabit().id).size > 1)
    }

    private fun launch(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun waitForActivity(): Activity {
        var activity: Activity? = null
        waitUntil {
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            }
            activity != null
        }
        return activity!!
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition not met within $timeoutMs ms", condition())
    }
}
