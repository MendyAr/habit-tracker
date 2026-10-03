package io.github.mendyar.habittracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitEditActivity
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.stats.StatsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
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

        // Scroll to the chart and entries.
        instrumentation.runOnMainSync {
            val scroll = activity.findViewById<ScrollView>(R.id.stats_scroll)
            scroll.scrollTo(0, activity.findViewById<View>(R.id.stats_caption).bottom)
        }
        screenshot("2_statistics_history")

        instrumentation.runOnMainSync { activity.findViewById<TextView>(R.id.period_week).performClick() }
        instrumentation.waitForIdleSync()
        assertEquals(Period.WEEK, repository.settings.period(id))
        instrumentation.runOnMainSync { activity.findViewById<ScrollView>(R.id.stats_scroll).scrollTo(0, 0) }
        screenshot("6_statistics_week")
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

    /**
     * The real user flow through the launcher: tap the app icon (logs and returns
     * to the home screen), then long-press it and open a habit's statistics from
     * the shortcut popup. Skipped where the launcher has not placed the app icon
     * on the home screen.
     */
    @Test
    fun launcherIconTapAndLongPress() {
        val id = seed()
        LauncherSync.refresh(context)
        device.pressHome()
        device.waitForIdle()

        val appName = context.getString(R.string.app_name)
        val icon = device.wait(Until.findObject(By.desc(appName)), 3_000) ?: device.findObject(By.text(appName))
        assumeTrue("The launcher shows no '$appName' icon on the home screen", icon != null)
        val before = repository.timestamps(id).size
        icon!!.click()
        Thread.sleep(450)
        screenshot("5_log_animation", settleMs = 0)
        waitUntil { repository.timestamps(id).size == before + 1 }
        waitUntil { resumedActivity() == null }

        // Long-press shortcuts exist from Android 7.1 on.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        device.wait(Until.findObject(By.desc(appName)), 3_000)?.longClick()
        val shortcut = device.wait(Until.findObject(By.textStartsWith("Smoking")), 3_000)
        assumeTrue("The launcher shows no long-press shortcuts", shortcut != null)
        screenshot("7_long_press")
        shortcut!!.click()
        waitUntil { resumedActivity() is StatsActivity }
        screenshot("8_from_shortcut")
    }

    private fun launch(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun resumedActivity(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return activity
    }

    private fun waitForActivity(): Activity {
        waitUntil { resumedActivity() != null }
        return resumedActivity()!!
    }

    /** Saves a screenshot, scaled to at most 540 px wide, for CI to collect. */
    private fun screenshot(name: String, settleMs: Long = 400) {
        if (settleMs > 0) {
            instrumentation.waitForIdleSync()
            Thread.sleep(settleMs)
        }
        val full: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val bitmap = if (full.width > 540) {
            Bitmap.createScaledBitmap(full, 540, full.height * 540 / full.width, true)
        } else {
            full
        }
        // Emulator images without external storage fall back to private storage (pulled with run-as).
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "screenshots").apply { mkdirs() }
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
