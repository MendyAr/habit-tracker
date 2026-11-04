package io.github.mendyar.habittracker.log

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.os.Looper
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.stats.StatsActivity
import io.github.mendyar.habittracker.ui.Async
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class LogActivityTest {

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
    fun pinnedIconLogsAndShowsTheConfirmation() {
        val habit = firstHabit()
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habit.id)).setup().get()

        awaitCondition { repository.timestamps(habit.id).size == 1 }
        // The system reports the end of the window transition; Robolectric does not.
        activity.onEnterAnimationComplete()
        val confirmation = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LogAnimationView
        awaitCondition { confirmation.isStarted }

        // Tapping the confirmation dismisses it at once. (That it also closes by itself is
        // checked on real devices by LogFlowTest; Robolectric's animation clock is unreliable.)
        confirmation.performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun pinnedIconLogsItsOwnHabit() {
        val other = firstHabit()
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup()

        awaitCondition { repository.timestamps(water.id).size == 1 }
        assertEquals(0, repository.timestamps(other.id).size)
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
    fun appIconOpensTheHabitListAndLogsNothing() {
        val water = firstHabit()
        val walk = repository.createHabit("Walk", "walk", null, HabitColors.ALL[2])
        val activity = Robolectric.buildActivity(LogActivity::class.java, launcherIntent()).setup().get()

        awaitCondition { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(HabitsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertTrue(activity.isFinishing)
        assertEquals(0, repository.timestamps(water.id).size + repository.timestamps(walk.id).size)
    }

    @Test
    fun appIconOpensTheStatisticsOfTheOnlyHabit() {
        val water = firstHabit()
        val activity = Robolectric.buildActivity(LogActivity::class.java, launcherIntent()).setup().get()

        awaitCondition { shadowOf(activity).peekNextStartedActivity() != null }
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(StatsActivity::class.java.name, started.component?.className)
        assertEquals(water.id, started.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1))
        assertEquals(0, repository.timestamps(water.id).size)
    }

    @Test
    fun appIconWithoutHabitsOpensTheEmptyListAndCreatesNothing() {
        val activity = Robolectric.buildActivity(LogActivity::class.java, launcherIntent()).setup().get()

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

    @Test
    fun aTapMomentsAfterTheLastEntryAsksFirst() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.log(water.id, System.currentTimeMillis() - 20_000)
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup().get()

        val dialog = awaitDialog()
        assertEquals(1, repository.timestamps(water.id).size) // nothing logged while asking
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitCondition { repository.timestamps(water.id).size == 2 }
        assertFalse(activity.isFinishing) // the confirmation plays next
    }

    @Test
    fun cancellingTheQuestionLogsNothing() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.log(water.id, System.currentTimeMillis() - 5_000)
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup().get()

        awaitDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        awaitCondition { activity.isFinishing }
        assertEquals(1, repository.timestamps(water.id).size)
    }

    @Test
    fun dontAskAgainLogsAndStopsAskingForThatHabit() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.log(water.id, System.currentTimeMillis() - 5_000)
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup()

        awaitDialog().getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        awaitCondition { repository.timestamps(water.id).size == 2 && !repository.settings.confirmDuplicates(water.id) }
        assertNull(repository.recentDuplicate(water.id, System.currentTimeMillis()))
    }

    @Test
    fun aTapAMinuteAfterTheLastEntryLogsRightAway() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.log(water.id, System.currentTimeMillis() - HabitRepository.DUPLICATE_WINDOW_MS - 1_000)
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup()
        awaitCondition { repository.timestamps(water.id).size == 2 }
    }

    @Test
    fun withAnimationsOffTheConfirmationStaysVisibleWithoutMotion() {
        // System setting "Remove animations": an animator would jump straight to its end.
        val confirmation = LogAnimationView(context)
        confirmation.motionEnabled = { false }
        var finished = false
        confirmation.onFinished = { finished = true }

        confirmation.start(HabitColors.ALL[1], "Water · 1 today", null)
        assertTrue(confirmation.isStarted)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
        assertFalse(finished) // still shown, not skipped

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(finished)
    }

    private fun awaitDialog(): AlertDialog {
        awaitCondition { (ShadowAlertDialog.getLatestAlertDialog() as? AlertDialog)?.isShowing == true }
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    private fun firstHabit() = repository.createHabit("Water", "water", null, HabitColors.ALL[1])

    /** What a launcher sends when the app icon is tapped (also the installer's Open button). */
    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}
