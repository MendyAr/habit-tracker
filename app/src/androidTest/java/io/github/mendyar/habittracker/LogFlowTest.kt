package io.github.mendyar.habittracker

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
        repository = HabitRepository.get(context)
    }

    @Test
    fun launcherIconLogsAndLeavesNoActivityBehind() {
        val habit = repository.defaultHabit()
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClass(context, LogActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.sourceBounds = Rect(300, 1200, 444, 1344)
        context.startActivity(intent)

        waitUntil { repository.timestamps(habit.id).size == 1 }
        // The confirmation lasts at most ~3 s (first run shows a hint), then the window closes.
        waitUntil(timeoutMs = 8_000) { resumedActivities() == 0 }
    }

    @Test
    fun pinnedShortcutIntentLogsItsHabit() {
        val default = repository.defaultHabit()
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        context.startActivity(Shortcuts.logIntent(context, water.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { repository.timestamps(water.id).size == 1 }
        assertEquals(0, repository.timestamps(default.id).size)
        waitUntil(timeoutMs = 8_000) { resumedActivities() == 0 }
    }

    @Test
    fun launcherSyncRunsOnThisApiLevel() {
        repository.defaultHabit()
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
}
