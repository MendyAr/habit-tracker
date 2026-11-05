package io.github.mendyar.habittracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Rect
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.log.LogActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks of the one-tap logging flow on a real device or emulator. */
@RunWith(AndroidJUnit4::class)
class LogFlowTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        HabitRepository.resetForTests()
        context.deleteDatabase("habits.db")
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        repository = HabitRepository.get(context)
        // The tutorial is tested on its own; here the app should open straight to its screens.
        repository.settings.lastSeenVersion = io.github.mendyar.habittracker.onboarding.Onboarding.versionCode(context)
    }

    @Test
    fun launcherIconOpensTheAppAndLogsNothing() {
        val habit = firstHabit()
        repository.createHabit("Walk", "walk", null, HabitColors.ALL[8])
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClass(context, LogActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.sourceBounds = Rect(300, 1200, 444, 1344)
        context.startActivity(intent)

        waitUntil { resumedActivity() is HabitsActivity }
        assertEquals(0, repository.timestamps(habit.id).size)
        val opened = resumedActivity()
        instrumentation.runOnMainSync { opened?.finish() }
    }

    @Test
    fun pinnedIconLogsAndLeavesNoActivityBehind() {
        val habit = firstHabit()
        repository.settings.setSuggestTriggers(habit.id, false)
        context.startActivity(Shortcuts.logIntent(context, habit.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { repository.timestamps(habit.id).size == 1 }
        // The confirmation lasts about 2 s, then the window closes.
        waitUntil(timeoutMs = 8_000) { resumedActivities() == 0 }
    }

    @Test
    fun pinnedShortcutIntentLogsItsHabit() {
        val default = repository.createHabit("Walk", "walk", null, HabitColors.ALL[8])
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.settings.setSuggestTriggers(water.id, false)
        context.startActivity(Shortcuts.logIntent(context, water.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { repository.timestamps(water.id).size == 1 }
        assertEquals(0, repository.timestamps(default.id).size)
        waitUntil(timeoutMs = 8_000) { resumedActivities() == 0 }
    }

    @Test
    fun afterLoggingAPickedTriggerTagsTheEntry() {
        val habit = firstHabit()
        context.startActivity(Shortcuts.logIntent(context, habit.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { repository.timestamps(habit.id).size == 1 }
        // The short confirmation, then the suggestion.
        var picker: io.github.mendyar.habittracker.log.TriggerPickerView? = null
        waitUntil(timeoutMs = 8_000) {
            val activity = resumedActivity() as? LogActivity
            if (activity != null) instrumentation.runOnMainSync { picker = activity.triggerPicker }
            picker != null
        }
        instrumentation.runOnMainSync { clickText(picker!!, "Craving") }
        waitUntil { repository.taggedEntries(habit.id).map { it.second } == listOf("Craving") }
        waitUntil(timeoutMs = 5_000) { resumedActivities() == 0 }
    }

    private fun clickText(view: android.view.View, text: String): Boolean {
        if (view is android.widget.TextView && view.text.toString() == text) return view.performClick()
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) if (clickText(view.getChildAt(i), text)) return true
        return false
    }

    @Test
    fun launcherSyncRunsOnThisApiLevel() {
        firstHabit()
        repository.createHabit("Walk", "walk", null, HabitColors.ALL[8])
        // Publishes shortcuts (Android 7.1+) and redraws widgets; must not throw on any version.
        LauncherSync.refresh(context)
        assertTrue(repository.habits().size == 2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            // What the launcher shows on long-press: one statistics entry per habit.
            val published = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.map { it.id }.toSet()
            val expected = repository.habits().map { Shortcuts.statsShortcutId(it.id) }.toSet()
            assertEquals(expected, published)
        }
    }

    private fun resumedActivity(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return activity
    }

    private fun resumedActivities(): Int {
        var count = 0
        instrumentation.runOnMainSync {
            count = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).size
        }
        return count
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition not met within $timeoutMs ms", condition())
    }

    /** The habit the first run creates for the app icon. */
    private fun firstHabit() = repository.createHabit("Smoking", "smoking", null, HabitColors.ALL[5])
}
