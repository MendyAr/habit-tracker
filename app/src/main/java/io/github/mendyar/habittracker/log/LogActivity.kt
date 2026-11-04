package io.github.mendyar.habittracker.log

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.DuplicateDialog
import io.github.mendyar.habittracker.ui.Formats

/**
 * Entry point of the launcher icon and of every pinned habit icon.
 *
 * A pinned habit icon records a timestamp, plays [LogAnimationView] in the middle
 * of the (still visible) home screen in a transparent window and finishes without
 * any transition, so the user never leaves the screen they tapped on. A tap moments
 * after the habit's last entry asks first, since it is usually a double tap.
 *
 * The main app icon logs nothing: it opens the app, on the habit list, or on the
 * statistics of the only habit. (This class stays the launcher entry because
 * launchers drop the home-screen icon of an app whose launcher component is renamed.)
 */
class LogActivity : Activity() {

    private lateinit var animation: LogAnimationView
    private var finishing = false
    private var result: Result? = null
    private var windowShown = false
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitions()
        if (savedInstanceState != null) {
            // Recreated after the timestamp was already recorded: never log twice.
            finishQuietly()
            return
        }
        layoutBehindSystemBars()

        animation = LogAnimationView(this)
        animation.onFinished = ::finishQuietly
        animation.setOnClickListener { animation.skip() }
        setContentView(animation)

        val habitId = intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1)
        if (habitId == -1L) {
            openApp()
            return
        }
        val now = System.currentTimeMillis()
        Async.load({ record(habitId, now) }) { outcome ->
            when (outcome) {
                // The habit was deleted: its greyed-out icon opens the habit list.
                null -> {
                    startActivity(
                        Intent(this, HabitsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                    finishQuietly()
                }
                is Outcome.Duplicate -> askAboutDuplicate(outcome)
                is Outcome.Logged -> {
                    result = outcome.result
                    startAnimationWhenVisible()
                }
            }
        }
        // Queued after the write on the same serial thread, so the animation is not delayed by it.
        Async.io { LauncherSync.afterLog(applicationContext) }
        startEnterFallback()
    }

    /** The main app icon: opens the habit list, or the statistics of the only habit. */
    private fun openApp() {
        Async.load({ HabitRepository.get(this).habits() }) { habits ->
            val target = habits.singleOrNull()?.let { Shortcuts.statsIntent(this, it.id) }
                ?: Intent(this, HabitsActivity::class.java)
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finishQuietly()
        }
    }

    private fun startEnterFallback() {
        // Safety net in case the system never reports the end of the window transition.
        Async.mainDelayed(ENTER_TIMEOUT_MS) {
            windowShown = true
            startAnimationWhenVisible()
        }
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        // Launchers animate the opening window; starting earlier would play part of the
        // confirmation while the window is not yet on screen.
        windowShown = true
        startAnimationWhenVisible()
    }

    private fun startAnimationWhenVisible() {
        val ready = result ?: return
        if (!windowShown || finishing || animation.isStarted) return
        haptic()
        animation.start(ready.color, ready.label, ready.hint)
    }

    private class Result(val color: Int, val label: String, val hint: String?)

    private sealed class Outcome {
        class Logged(val result: Result) : Outcome()

        /** Not logged: the habit was logged moments ago, at [previous]. */
        class Duplicate(val habit: Habit, val previous: Long, val now: Long) : Outcome()
    }

    /**
     * Runs on the I/O thread. Logs [habitId] unless that looks like an accidental
     * second tap; returns null when there is no such habit.
     */
    private fun record(habitId: Long, now: Long): Outcome? {
        val repository = HabitRepository.get(this)
        val habit = repository.habit(habitId) ?: return null
        repository.recentDuplicate(habit.id, now)?.let { return Outcome.Duplicate(habit, it, now) }
        return Outcome.Logged(log(habit, now))
    }

    /** Runs on the I/O thread: logs [habit] at [now] and describes the confirmation. */
    private fun log(habit: Habit, now: Long): Result {
        val repository = HabitRepository.get(this)
        repository.log(habit.id, now)

        val period = repository.settings.period(habit.id)
        val math = PeriodMath()
        val count = repository.count(habit.id, math.startOf(period, now), math.endOf(period, now))
        val label = getString(R.string.logged_label, habit.name, Formats(this).countPhrase(period, count))

        Shortcuts.reportUsed(this, habit.id)
        return Result(habit.color, label, null)
    }

    /** Asks over the home screen whether a second log moments after the last one is intended. */
    private fun askAboutDuplicate(duplicate: Outcome.Duplicate) {
        if (finishing) return
        val habit = duplicate.habit
        dialog = DuplicateDialog.recent(
            this,
            habit,
            duplicate.previous,
            duplicate.now,
            onLog = {
                Async.load({ log(habit, System.currentTimeMillis()) }) {
                    result = it
                    startAnimationWhenVisible()
                }
            },
            onCancel = ::finishQuietly,
        )
    }

    private fun haptic() {
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        animation.performHapticFeedback(feedback)
    }

    @Suppress("DEPRECATION")
    private fun layoutBehindSystemBars() {
        // Draw edge to edge so the animation can line up with the icon in screen coordinates.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun disableTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    private fun finishQuietly() {
        if (finishing) return
        finishing = true
        dialog?.dismiss()
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen (home pressed, screen off) ends the confirmation.
        finishQuietly()
    }

    private companion object {
        const val ENTER_TIMEOUT_MS = 1_000L
    }
}
