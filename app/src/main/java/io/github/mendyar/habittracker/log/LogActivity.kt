package io.github.mendyar.habittracker.log

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.Formats

/**
 * Entry point of the launcher icon and of every pinned habit icon.
 *
 * It records a timestamp, plays [LogAnimationView] over the (still visible)
 * home screen in a transparent window and finishes without any transition, so
 * the user never leaves the screen they tapped on.
 */
class LogActivity : Activity() {

    private lateinit var animation: LogAnimationView
    private var finishing = false
    private var anchor: Rect? = null
    private var result: Result? = null
    private var windowShown = false

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
        anchor = intent.sourceBounds
        val now = System.currentTimeMillis()
        Async.load({ record(habitId, now) }) {
            result = it
            startAnimationWhenVisible()
        }
        // Queued after the write on the same serial thread, so the animation is not delayed by it.
        Async.io { LauncherSync.refresh(applicationContext) }
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
        animation.start(ready.color, anchor, ready.label, ready.hint)
    }

    private class Result(val color: Int, val label: String, val hint: String?)

    /** Runs on the I/O thread. */
    private fun record(habitId: Long, now: Long): Result {
        val repository = HabitRepository.get(this)
        val habit = repository.habitOrDefault(habitId)
        repository.log(habit.id, now)

        val settings = repository.settings
        val period = settings.period(habit.id)
        val math = PeriodMath()
        val count = repository.count(habit.id, math.startOf(period, now), math.endOf(period, now))
        val label = getString(R.string.logged_label, Formats(this).countPhrase(period, count))
        val hint = if (!settings.hintShown) {
            settings.hintShown = true
            getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) R.string.hint_long_press else R.string.hint_stats_icon)
        } else {
            null
        }

        Shortcuts.reportUsed(this, habit.id)
        return Result(habit.color, label, hint)
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
