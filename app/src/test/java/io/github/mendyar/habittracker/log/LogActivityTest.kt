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

        awaitCondition { repository.timestamps(repository.defaultHabit().id).size == 1 }
        // Let the confirmation animation (well under 4 s, including the first-run hint) play out.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun pinnedIconLogsItsOwnHabit() {
        val default = repository.defaultHabit()
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup()

        awaitCondition { repository.timestamps(water.id).size == 1 }
        assertEquals(0, repository.timestamps(default.id).size)
    }

    @Test
    fun aRecreatedActivityNeverLogsTwice() {
        val habit = repository.defaultHabit()
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id))
            .setup(Bundle())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        Thread.sleep(200)
        assertEquals(0, repository.timestamps(habit.id).size)
    }

    @Test
    fun firstLogShowsTheHintOnlyOnce() {
        val habit = repository.defaultHabit()
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id)).setup()
        awaitCondition { repository.settings.hintShown }
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id)).setup()
        awaitCondition { repository.timestamps(habit.id).size == 2 }
        assertTrue(repository.settings.hintShown)
    }

    @Test
    fun loggingPublishesLongPressStatisticsShortcuts() {
        val habit = repository.defaultHabit()
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id)).setup()
        val manager = context.getSystemService(ShortcutManager::class.java)
        awaitCondition { manager.dynamicShortcuts.isNotEmpty() }
        assertEquals(listOf(Shortcuts.statsShortcutId(habit.id)), manager.dynamicShortcuts.map { it.id })
    }
}
