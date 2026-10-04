package io.github.mendyar.habittracker

import android.animation.ValueAnimator
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup
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
import io.github.mendyar.habittracker.log.LogActivity
import io.github.mendyar.habittracker.log.LogAnimationView
import io.github.mendyar.habittracker.widget.HabitWidgetProvider
import io.github.mendyar.habittracker.widget.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import java.util.regex.Pattern

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
        val smoking = firstHabit().copy(name = "Smoking", icon = "smoking", color = HabitColors.ALL[5])
        repository.updateHabit(smoking)
        val random = Random(7)
        val now = System.currentTimeMillis()
        val day = 24 * 3_600_000L
        val math = PeriodMath()
        // Cigarettes cluster around the same moments of the day: after waking up, coffee
        // breaks, lunch, the commute and the evening.
        val usual = doubleArrayOf(7.4, 10.2, 12.9, 15.6, 18.1, 20.4, 22.3, 11.5, 17.0, 21.3, 9.0, 14.3, 16.4, 19.2, 23.0)
        for (d in 60 downTo 1) {
            val midnight = math.startOf(Period.DAY, now - d * day)
            val perDay = 4 + d / 6 + random.nextInt(3)
            for (i in 0 until minOf(perDay, usual.size)) {
                val hours = usual[i] + random.nextGaussian() * 0.35
                repository.log(smoking.id, midnight + (hours * 3_600_000L).toLong())
            }
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

        // Scroll to the time-of-day chart, which peaks at one of the usual times.
        val readout = activity.findViewById<TextView>(R.id.time_readout)
        waitUntil { readout.text.isNotEmpty() }
        instrumentation.runOnMainSync {
            val scroll = activity.findViewById<ScrollView>(R.id.stats_scroll)
            scroll.scrollTo(0, activity.findViewById<View>(R.id.time_card).top - 24)
        }
        screenshot("9_time_of_day")

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
        waitUntil { activity.findViewById<ViewGroup>(R.id.habits_list).childCount == 3 }
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
     * The real user flow through the launcher: tapping the app icon logs a
     * timestamp and returns to the home screen. Then the confirmation is shown
     * again, anchored on the icon's bounds, to screenshot it mid-animation
     * (emulator screenshots are too slow to catch it after a real tap).
     * Skipped where the launcher has not placed the app icon on the home screen.
     */
    @Test
    fun launcherIconTap() {
        val id = seed()
        LauncherSync.refresh(context)
        device.pressHome()
        device.waitForIdle()
        dismissAnrDialog()

        val appName = context.getString(R.string.app_name)
        // The icon, not a widget (launchers describe widgets by the app's name too).
        val iconSelector = By.desc(appName).clazz("android.widget.TextView")
        val icon = device.wait(Until.findObject(iconSelector), 3_000) ?: device.findObject(By.text(appName))
        assumeTrue("The launcher shows no '$appName' icon on the home screen", icon != null)
        val bounds = icon!!.visibleBounds
        val before = repository.timestamps(id).size
        icon.click()
        waitUntil { repository.timestamps(id).size == before + 1 }
        waitUntil { resumedActivity() == null }

        Thread.sleep(1_500)
        val intent = Intent(Intent.ACTION_MAIN).setClass(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.sourceBounds = bounds
        context.startActivity(intent)
        var view: LogAnimationView? = null
        waitUntil {
            val activity = resumedActivity() as? LogActivity
            if (activity != null) {
                instrumentation.runOnMainSync {
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    view = (content.getChildAt(0) as? LogAnimationView)?.takeIf { it.isStarted }
                }
            }
            view != null
        }
        // Emulators render far behind wall-clock time, so freeze the running animation on its
        // fully shown frame (disc, check and count) before taking the picture.
        instrumentation.runOnMainSync { freeze(view!!, atMs = 700f) }
        Thread.sleep(500)
        screenshot("5_log_animation", settleMs = 0)
        waitUntil { repository.timestamps(id).size == before + 2 }
        instrumentation.runOnMainSync { view!!.skip() }
    }

    /**
     * The real "Add One-Tap Widget" flow with the system launcher: choose a size,
     * accept the launcher's dialog, and land on the home screen with the widget.
     * Skipped where adding widgets from apps is not supported (before Android 9 the
     * size cannot be chosen).
     */
    @Test
    fun addingAWidgetInAChosenSize() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        assumeTrue(AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported)
        seed()
        val walk = repository.createHabit("Walk", "walk", null, HabitColors.ALL[2])
        launch(HabitEditActivity.intent(context, walk.id))
        val editor = waitForActivity()
        waitUntil { editor.findViewById<TextView>(R.id.edit_name).text.toString() == "Walk" }
        instrumentation.runOnMainSync { editor.findViewById<View>(R.id.edit_add_widget).performClick() }

        val medium = device.wait(Until.findObject(By.desc(context.getString(R.string.widget_size_description, 2, 2))), 5_000)
        assertNotNull("No widget size dialog", medium)
        screenshot("7_widget_size")
        medium!!.click()

        // The launcher's own confirmation ("Add to home screen" / "Add automatically").
        val add = device.wait(Until.findObject(By.clickable(true).text(Pattern.compile("(?i)add( to home screen| automatically)?"))), 8_000)
        assertNotNull("No launcher confirmation; screen shows ${device.currentPackageName}", add)
        add!!.click()

        waitUntil(timeoutMs = 15_000) { HabitWidgetProvider.widgetFor(context, walk.id) != null }
        val id = HabitWidgetProvider.widgetFor(context, walk.id)!!
        val manager = AppWidgetManager.getInstance(context)
        assertTrue(id in manager.getAppWidgetIds(ComponentName(context, WidgetSize.MEDIUM.provider)))
        // Back on the home screen, which shows the widget.
        val launcher = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        )!!.activityInfo.packageName
        waitUntil { device.currentPackageName == launcher }
        device.wait(Until.findObject(By.text("Walk")), 3_000)
        screenshot("8_widget_added")
    }

    /** Pauses [view]'s private animator at [atMs] (debug builds are not obfuscated). */
    private fun freeze(view: LogAnimationView, atMs: Float) {
        val animator = LogAnimationView::class.java.getDeclaredField("animator")
            .apply { isAccessible = true }.get(view) as ValueAnimator
        animator.pause()
        LogAnimationView::class.java.getDeclaredField("elapsed").apply { isAccessible = true }.setFloat(view, atMs)
        view.invalidate()
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

    /** Closes "… isn't responding" dialogs that slow CI emulators show for system apps. */
    private fun dismissAnrDialog() {
        if (device.findObject(By.textContains("isn't responding")) != null) {
            device.findObject(By.text("Wait"))?.click()
            device.waitForIdle()
        }
    }

    /** Saves a screenshot, scaled to at most 540 px wide, for CI to collect. */
    private fun screenshot(name: String, settleMs: Long = 400) {
        if (settleMs > 0) {
            dismissAnrDialog()
            instrumentation.waitForIdleSync()
            Thread.sleep(settleMs)
        }
        // takeScreenshot() occasionally returns null on a busy emulator.
        repeat(3) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            if (bitmap != null) return save(name, bitmap)
            Thread.sleep(300)
        }
    }

    private fun save(name: String, full: Bitmap) {
        val bitmap = if (full.width > 540) Bitmap.createScaledBitmap(full, 540, full.height * 540 / full.width, true) else full
        FileOutputStream(File(screenshotDir(), "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Emulator images without external storage fall back to private storage (pulled with run-as).
    private fun screenshotDir() = File(context.getExternalFilesDir(null) ?: context.filesDir, "screenshots").apply { mkdirs() }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition not met within $timeoutMs ms", condition())
    }

    /** The habit the first run creates for the app icon. */
    private fun firstHabit() = repository.run {
        ensureFirstRun()
        defaultHabit()!!
    }
}
