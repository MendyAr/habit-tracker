package io.github.mendyar.habittracker.log

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.data.Triggers
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.onboarding.Onboarding
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
        // The tutorial is tested in OnboardingTest; here the app icon should open the app directly.
        repository.settings.lastSeenVersion = Onboarding.versionCode(context).coerceAtLeast(1)
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
        val confirmation = activity.confirmationView
        awaitCondition { confirmation.isStarted }

        // Tapping the confirmation dismisses it at once. (That it also closes by itself is
        // checked on real devices by LogFlowTest; Robolectric's animation clock is unreliable.)
        touch(confirmation, 10f, 10f)
        // A count habit then suggests a trigger; skipping it closes the window.
        findText(pickerOf(activity)!!, context.getString(R.string.picker_skip))!!.performClick()
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

    @Test
    fun durationTapsFollowStartingAlreadyStartedEndedAlreadyEnded() {
        val breathing = repository.createHabit("Box breathing", "box_breathing", null, HabitColors.ALL[3], HabitType.DURATION)

        // First tap: starts.
        assertEquals("Box breathing · Starting", tapAndRead(breathing.id))
        val started = repository.running(breathing.id)!!

        // Within 3 seconds of the start: nothing changes.
        assertEquals("Box breathing · Already Started", tapAndRead(breathing.id))
        assertEquals(started, repository.running(breathing.id))

        // Later: ends, with its length large on the line below. (Pretend it started 4 minutes ago.)
        repository.deleteEntry(started.id)
        repository.log(breathing.id, System.currentTimeMillis() - 240_000)
        assertEquals("Box breathing · Ended\n4:00", tapAndRead(breathing.id))
        assertNull(repository.running(breathing.id))
        assertEquals(1, repository.sessions(breathing.id).size)

        // Within 3 seconds of the end: nothing changes, and the same length shows.
        assertEquals("Box breathing · Already Ended\n4:00", tapAndRead(breathing.id))
        assertNull(repository.running(breathing.id))
        assertEquals(1, repository.sessions(breathing.id).size)

        // After that, a tap starts a new session. (Pretend the last one ended 5 seconds ago.)
        val last = repository.sessions(breathing.id).single()
        repository.deleteEntry(last.id)
        repository.addSession(breathing.id, System.currentTimeMillis() - 245_000, 240_000)
        assertEquals("Box breathing · Starting", tapAndRead(breathing.id))
        assertTrue(repository.running(breathing.id) != null)
    }

    @Test
    fun tapsOnTheWidgetsSpotCountAsTapsOfTheWidget() {
        // The launcher reports where the tapped widget is; a tap there (with a little
        // slack) during the confirmation is another tap of it, anywhere else dismisses.
        // (Tapping the real widget twice is checked on emulators by ScreensTest.)
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val activity = widgetTap(water.id)
        val slack = 8 * context.resources.displayMetrics.density
        assertTrue(activity.isOnTappedSpot(150f, 150f))
        assertTrue(activity.isOnTappedSpot(100f - slack + 1, 150f))
        assertFalse(activity.isOnTappedSpot(20f, 400f))
        assertFalse(activity.isOnTappedSpot(150f, 200f + slack + 2))

        // Without a widget position (e.g. an old pinned icon), every tap dismisses.
        val icon = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, water.id)).setup().get()
        assertFalse(icon.isOnTappedSpot(150f, 150f))
    }

    @Test
    fun theConfirmationShowsALargeValueAndShrinksLongTextsToFit() {
        val confirmation = LogAnimationView(context)
        confirmation.motionEnabled = { false }
        confirmation.layout(0, 0, 400, 800)
        val long = "A habit with a really very extremely long name that would never fit · Ended"
        confirmation.start(HabitColors.ALL[1], long, null, value = "12:34")
        assertEquals("$long\n12:34", confirmation.shownText)
        // Drawing measures and scales every line; it must not throw.
        confirmation.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(400, 800, android.graphics.Bitmap.Config.ARGB_8888)))
        confirmation.reset()
        assertFalse(confirmation.isStarted)
    }

    @Test
    fun afterLoggingTheTriggerSuggestionTagsThePickedOne() {
        val smoking = repository.createHabit("Smoking", "smoking", null, HabitColors.ALL[5])
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, smoking.id)).setup().get()
        activity.onEnterAnimationComplete()
        val confirmation = confirmationOf(activity)
        awaitCondition { confirmation.isStarted }
        // Logged at once, before any trigger is chosen.
        assertEquals(1, repository.timestamps(smoking.id).size)

        confirmation.skip() // the short confirmation ends
        val picker = pickerOf(activity)!!
        assertEquals(Triggers.PRESETS, picker.offered)
        assertFalse(activity.isFinishing)

        findText(picker, "Coffee")!!.performClick()
        awaitCondition { repository.taggedEntries(smoking.id).map { it.second } == listOf("Coffee") }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun skippingTheTriggerSuggestionLeavesTheEntryUntagged() {
        val smoking = repository.createHabit("Smoking", "smoking", null, HabitColors.ALL[5])
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, smoking.id)).setup().get()
        activity.onEnterAnimationComplete()
        val confirmation = confirmationOf(activity)
        awaitCondition { confirmation.isStarted }
        confirmation.skip()

        findText(pickerOf(activity)!!, context.getString(R.string.picker_skip))!!.performClick()
        assertTrue(activity.isFinishing)
        Async.awaitIdle()
        assertEquals(1, repository.timestamps(smoking.id).size)
        assertTrue(repository.taggedEntries(smoking.id).isEmpty())
    }

    @Test
    fun anIgnoredTriggerSuggestionClosesByItself() {
        val smoking = repository.createHabit("Smoking", "smoking", null, HabitColors.ALL[5])
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, smoking.id)).setup().get()
        activity.onEnterAnimationComplete()
        val confirmation = confirmationOf(activity)
        awaitCondition { confirmation.isStarted }
        confirmation.skip()
        assertTrue(pickerOf(activity) != null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertTrue(activity.isFinishing)
        assertTrue(repository.taggedEntries(smoking.id).isEmpty())
    }

    @Test
    fun aTypedTriggerTagsTheEntryAndIsOfferedNextTime() {
        val smoking = repository.createHabit("Smoking", "smoking", null, HabitColors.ALL[5])
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, smoking.id)).setup().get()
        activity.onEnterAnimationComplete()
        val confirmation = confirmationOf(activity)
        awaitCondition { confirmation.isStarted }
        confirmation.skip()

        findText(pickerOf(activity)!!, context.getString(R.string.picker_add))!!.performClick()
        val dialog = awaitDialog()
        findEditText(dialog.window!!.decorView)!!.setText("  Phone   Call ")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        awaitCondition { repository.taggedEntries(smoking.id).map { it.second } == listOf("Phone Call") }
        assertEquals(Triggers.PRESETS + "Phone Call", repository.settings.triggers(smoking.id))
    }

    @Test
    fun noTriggerSuggestionWhenTurnedOffOrForDurationHabits() {
        val smoking = repository.createHabit("Smoking", "smoking", null, HabitColors.ALL[5])
        repository.settings.setSuggestTriggers(smoking.id, false)
        val breathing = repository.createHabit("Box breathing", "box_breathing", null, HabitColors.ALL[3], HabitType.DURATION)
        for (id in listOf(smoking.id, breathing.id)) {
            val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, id)).setup().get()
            activity.onEnterAnimationComplete()
            val confirmation = confirmationOf(activity)
            awaitCondition { confirmation.isStarted }
            confirmation.skip()
            assertNull(pickerOf(activity))
            assertTrue(activity.isFinishing)
        }
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun longTriggerNamesWrapInsideTheirChipsInsteadOfBeingCut() {
        val long = "Arguing with someone about something important"
        val picker = TriggerPickerView(context, HabitColors.ALL[5], "Smoking · 3 today", listOf("Coffee", long), {}, {}, {})
        val width = 360 * context.resources.displayMetrics.density
        picker.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width.toInt(), android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec((640 * context.resources.displayMetrics.density).toInt(), android.view.View.MeasureSpec.EXACTLY),
        )
        picker.layout(0, 0, picker.measuredWidth, picker.measuredHeight)
        val chip = findText(picker, long) as android.widget.TextView
        // Fully shown: as many lines as it needs, none of them ellipsized.
        assertTrue(chip.lineCount >= 2)
        assertTrue((0 until chip.lineCount).all { chip.layout.getEllipsisCount(it) == 0 })
        assertTrue(chip.right <= picker.width)
    }

    @Test
    fun aForgottenSessionAsksToFinishOrDiscard() {
        val breathing = repository.createHabit("Box breathing", "box_breathing", null, HabitColors.ALL[3], HabitType.DURATION)
        repository.log(breathing.id, System.currentTimeMillis() - 5 * 3_600_000L)
        Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, breathing.id)).setup()

        awaitDialog().getButton(DialogInterface.BUTTON_NEUTRAL).performClick() // Discard
        awaitCondition { repository.running(breathing.id) == null }
        assertTrue(repository.sessions(breathing.id).isEmpty())
    }

    /** Opens the logging window for [habitId] and returns what its confirmation says. */
    private fun tapAndRead(habitId: Long): String {
        val activity = Robolectric.buildActivity(LogActivity::class.java, Shortcuts.logIntent(context, habitId)).setup().get()
        activity.onEnterAnimationComplete()
        val confirmation = confirmationOf(activity)
        awaitCondition { confirmation.isStarted }
        Async.awaitIdle()
        val text = confirmation.shownText
        touch(confirmation, 1f, 1f) // dismiss
        return text
    }

    private fun confirmationOf(activity: LogActivity): LogAnimationView = activity.confirmationView

    /** Opens the logging window as a tap on a widget at (100, 100)–(200, 200) does. */
    private fun widgetTap(habitId: Long): LogActivity {
        val intent = Shortcuts.logIntent(context, habitId).apply { sourceBounds = Rect(100, 100, 200, 200) }
        val activity = Robolectric.buildActivity(LogActivity::class.java, intent).setup().get()
        // The launcher's widget position, on the intent the activity holds.
        if (activity.intent.sourceBounds == null) activity.intent.sourceBounds = Rect(100, 100, 200, 200)
        activity.onEnterAnimationComplete()
        return activity
    }

    private fun touch(view: android.view.View, x: Float, y: Float) {
        val time = android.os.SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(time, time, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun pickerOf(activity: LogActivity): TriggerPickerView? = activity.triggerPicker

    private fun findText(view: android.view.View, text: String): android.view.View? {
        if (view is android.widget.TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findText(view.getChildAt(i), text)?.let { return it }
        return null
    }

    private fun findEditText(view: android.view.View): android.widget.EditText? {
        if (view is android.widget.EditText) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findEditText(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun awaitDialog(): AlertDialog {
        awaitCondition { (ShadowAlertDialog.getLatestAlertDialog() as? AlertDialog)?.isShowing == true }
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    private fun firstHabit() = repository.createHabit("Water", "water", null, HabitColors.ALL[1])

    /** What a launcher sends when the app icon is tapped (also the installer's Open button). */
    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}
