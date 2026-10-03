package io.github.mendyar.habittracker.log

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class LogActivityTest {

    private lateinit var context: Context
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        HabitRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        repository = HabitRepository.get(context)
    }

    @After
    fun tearDown() {
        HabitRepository.resetForTests()
    }

    @Test
    fun launcherTapLogsTheDefaultHabitAndClosesItself() {
        val intent = Intent(Intent.ACTION_MAIN).setClass(context, LogActivity::class.java)
        intent.sourceBounds = Rect(100, 800, 244, 944)
        val controller = Robolectric.buildActivity(LogActivity::class.java, intent).setup()

        awaitCondition { repository.defaultHabit()?.let { repository.timestamps(it.id).size } == 1 }
        // Let the confirmation play out: up to 1 s waiting for the window, then ~3.1 s with
        // the first-run hint.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun pinnedIconLogsItsOwnHabit() {
        val default = firstHabit()
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup()

        awaitCondition { repository.timestamps(water.id).size == 1 }
        assertEquals(0, repository.timestamps(default.id).size)
    }

    @Test
    fun aRecreatedActivityNeverLogsTwice() {
        val habit = firstHabit()
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id))
            .setup(Bundle())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        Thread.sleep(200)
        assertEquals(0, repository.timestamps(habit.id).size)
    }

    @Test
    fun firstLogShowsTheHintOnlyOnce() {
        val habit = firstHabit()
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id)).setup()
        awaitCondition { repository.settings.hintShown }
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id)).setup()
        awaitCondition { repository.timestamps(habit.id).size == 2 }
        assertTrue(repository.settings.hintShown)
    }

    @Test
    fun launcherTapWithoutAnAppIconHabitOpensTheHabitList() {
        val habit = firstHabit()
        repository.setDefaultHabit(null)
        val activity = Robolectric.buildActivity(LogActivity::class.java, Intent(Intent.ACTION_MAIN)).setup().get()

        awaitCondition { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(HabitsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertTrue(activity.isFinishing)
        assertEquals(0, repository.timestamps(habit.id).size)
    }

    @Test
    fun launcherTapAfterDeletingEveryHabitCreatesNothing() {
        repository.deleteHabit(firstHabit().id)
        val activity = Robolectric.buildActivity(LogActivity::class.java, Intent(Intent.ACTION_MAIN)).setup().get()

        awaitCondition { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(HabitsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertTrue(repository.habits().isEmpty())
    }

    @Test
    fun aDeletedHabitsPinnedIconLogsNothing() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.deleteHabit(water.id)
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup().get()
        awaitCondition { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(0, repository.timestamps(water.id).size)
    }

    @Test
    fun loggingPublishesLongPressStatisticsShortcuts() {
        val habit = firstHabit()
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id)).setup()
        val manager = context.getSystemService(ShortcutManager::class.java)
        awaitCondition { manager.dynamicShortcuts.isNotEmpty() }
        assertEquals(listOf(Shortcuts.statsShortcutId(habit.id)), manager.dynamicShortcuts.map { it.id })
    }

    /** The habit the first run creates for the app icon. */
    private fun firstHabit() = repository.run {
        ensureFirstRun()
        defaultHabit()!!
    }
}
