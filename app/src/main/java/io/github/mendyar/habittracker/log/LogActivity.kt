package io.github.mendyar.habittracker.log

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.graphics.Rect
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.data.Entry
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.data.Sessions
import io.github.mendyar.habittracker.data.Triggers
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.onboarding.Onboarding
import io.github.mendyar.habittracker.onboarding.OnboardingActivity
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.DuplicateDialog
import io.github.mendyar.habittracker.ui.Formats
import io.github.mendyar.habittracker.widget.HabitWidgetProvider

/**
 * Entry point of the launcher icon, of every habit widget and of habit icons pinned
 * by earlier versions.
 *
 * A habit's widget (or icon) records a timestamp, plays [LogAnimationView] in the
 * middle of the (still visible) home screen in a transparent window and finishes
 * without any transition, so the user never leaves the screen they tapped on. A tap
 * moments after the habit's last entry asks first, since it is usually a double tap.
 * Duration habits follow [Sessions]. While the confirmation shows, a tap on the spot
 * of the widget that opened it counts as another tap of that widget.
 *
 * After logging a count habit, a short confirmation is followed by a suggestion to
 * tag what set it off ([TriggerPickerView]). The entry is already logged: skipping or
 * ignoring it leaves the entry untagged.
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
    private var habitId = -1L

    /** Whether a tap is being recorded (a new one waits until it is). */
    private var recording = false
    private var picker: TriggerPickerView? = null

    /** The confirmation on screen (for tests). */
    internal val confirmationView: LogAnimationView get() = animation

    /** The trigger suggestion, while it shows (for tests). */
    internal val triggerPicker: TriggerPickerView? get() = picker
    private lateinit var root: FrameLayout

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
        animation.onFinished = ::confirmationDone
        animation.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                // Screen coordinates: the window is full screen, but go by the view's own position.
                val origin = IntArray(2).also { animation.getLocationOnScreen(it) }
                if (onTappedSpot(origin[0] + event.x, origin[1] + event.y)) tapAgain() else animation.skip()
            }
            true
        }
        // Accessibility services click instead of touching.
        animation.setOnClickListener { animation.skip() }
        root = FrameLayout(this)
        root.addView(animation)
        setContentView(root)

        habitId = intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1)
        if (habitId == -1L) {
            openApp()
            return
        }
        tap()
        startEnterFallback()
    }

    /** Records a tap of the habit and shows its outcome. */
    private fun tap() {
        val now = System.currentTimeMillis()
        recording = true
        Async.load({ record(habitId, now) }) { outcome ->
            recording = false
            if (finishing) return@load
            when (outcome) {
                // The habit was deleted: its greyed-out icon opens the habit list.
                null -> {
                    startActivity(
                        Intent(this, HabitsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                    finishQuietly()
                }
                is Outcome.Duplicate -> {
                    animation.reset()
                    askAboutDuplicate(outcome)
                }
                is Outcome.LongRunning -> {
                    animation.reset()
                    askAboutLongSession(outcome)
                }
                is Outcome.Logged -> show(outcome.result)
            }
        }
        // Queued after the write on the same serial thread, so the animation is not delayed by it.
        Async.io { LauncherSync.afterLog(applicationContext) }
    }

    /**
     * Whether the point ([x], [y]) on the screen is where the tapped widget or icon is
     * (with a little slack): the user tapped it again through this transparent window.
     */
    private fun onTappedSpot(x: Float, y: Float): Boolean {
        val bounds = intent.sourceBounds ?: return false
        if (habitId == -1L || bounds.isEmpty) return false
        val slack = (TAP_SLACK_DP * resources.displayMetrics.density).toInt()
        return Rect(bounds).apply { inset(-slack, -slack) }.contains(x.toInt(), y.toInt())
    }

    /** Another tap of the same widget while its confirmation shows. */
    private fun tapAgain() {
        if (finishing || recording || dialog?.isShowing == true || picker != null) return
        tap()
    }

    /** Shows the confirmation of [outcome], replacing the one on screen. */
    private fun show(outcome: Result) {
        result = outcome
        animation.reset()
        startAnimationWhenVisible()
    }

    /**
     * The main app icon: opens the habit list, or the statistics of the only habit,
     * with the tutorial (first install) or "What's new" (after an update) on top, once.
     */
    private fun openApp() {
        val app = applicationContext
        Async.load({ HabitRepository.get(app).habits() to Onboarding.pending(app) }) { (habits, onboarding) ->
            val target = habits.singleOrNull()?.let { Shortcuts.statsIntent(this, it.id) }
                ?: Intent(this, HabitsActivity::class.java)
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            onboarding?.let { startActivity(OnboardingActivity.intent(this, it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
        animation.start(ready.color, ready.label, ready.hint, ready.mark, ready.value, short = ready.suggestTriggers)
    }

    /** The confirmation ended: suggest a trigger if this entry gets one, else close. */
    private fun confirmationDone() {
        val logged = result
        if (logged == null || !logged.suggestTriggers || picker != null || finishing) {
            finishQuietly()
            return
        }
        showPicker(logged)
    }

    private fun showPicker(logged: Result) {
        val app = applicationContext
        val view = TriggerPickerView(
            this,
            logged.color,
            logged.label,
            logged.triggers,
            onPick = { name ->
                Async.io { HabitRepository.get(app).setTrigger(logged.entryId, name) }
                Async.mainDelayed(PICKED_MS) { finishQuietly() }
            },
            onAdd = { askForNewTrigger(logged) },
            onSkip = ::finishQuietly,
        )
        picker = view
        root.addView(view)
        view.appear(animation.motionEnabled())
        // Ignored: the entry stays untagged.
        Async.mainDelayed(PICKER_TIMEOUT_MS) { if (dialog?.isShowing != true) finishQuietly() }
    }

    /** "+ Add": a new trigger, typed; it tags this entry and is offered from now on. */
    private fun askForNewTrigger(logged: Result) {
        val app = applicationContext
        val field = EditText(this).apply {
            setHint(R.string.picker_add_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(Triggers.MAX_LENGTH))
            setSingleLine()
        }
        val box = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(field)
        }
        dialog = AlertDialog.Builder(this, R.style.Theme_HabitTracker_Dialog)
            .setTitle(R.string.picker_add_title)
            .setView(box)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = Triggers.clean(field.text.toString())
                if (name != null) {
                    Async.io {
                        val repository = HabitRepository.get(app)
                        repository.setTrigger(logged.entryId, name)
                        repository.settings.setTriggers(logged.habitId, Triggers.added(repository.settings.triggers(logged.habitId), name))
                    }
                }
                finishQuietly()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> finishQuietly() }
            .setOnCancelListener { finishQuietly() }
            .create()
            .apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                show()
            }
        field.requestFocus()
    }

    private class Result(
        val color: Int,
        val label: String,
        val hint: String?,
        val mark: LogAnimationView.Mark = LogAnimationView.Mark.CHECK,
        /** Shown large under the label, e.g. a session's length. */
        val value: String? = null,
        /** The entry just logged, and whether to suggest tagging what set it off. */
        val entryId: Long = -1,
        val habitId: Long = -1,
        val suggestTriggers: Boolean = false,
        val triggers: List<String> = emptyList(),
    )

    private sealed class Outcome {
        class Logged(val result: Result) : Outcome()

        /** Not logged: the habit was logged moments ago, at [previous]. */
        class Duplicate(val habit: Habit, val previous: Long, val now: Long) : Outcome()

        /** A duration habit's session has been running for very long: probably forgotten. */
        class LongRunning(val habit: Habit, val session: Entry, val now: Long) : Outcome()
    }

    /**
     * Runs on the I/O thread. Logs [habitId] unless that looks like an accidental
     * second tap; returns null when there is no such habit.
     */
    private fun record(habitId: Long, now: Long): Outcome? {
        val repository = HabitRepository.get(this)
        val habit = repository.habit(habitId) ?: return null
        if (habit.type == HabitType.DURATION) {
            val session = repository.running(habit.id)
            if (session != null && now - session.timestamp > Sessions.LONG_RUNNING_MS) return Outcome.LongRunning(habit, session, now)
            return Outcome.Logged(toggleSession(habit, now))
        }
        repository.recentDuplicate(habit.id, now)?.let { return Outcome.Duplicate(habit, it, now) }
        return Outcome.Logged(log(habit, now))
    }

    /**
     * Runs on the I/O thread: a tap of the duration habit [habit] at [now], following
     * [Sessions]: starts a session, finishes the running one, or (moments after
     * either) changes nothing and says so.
     */
    private fun toggleSession(habit: Habit, now: Long): Result {
        val repository = HabitRepository.get(this)
        val formats = Formats(this)
        val session = repository.running(habit.id)
        val stopwatch = LogAnimationView.Mark.STOPWATCH
        val result = if (session == null) {
            val last = repository.lastFinished(habit.id)
            val lastEnd = last?.end
            if (last != null && lastEnd != null && now - lastEnd in 0 until Sessions.GRACE_MS) {
                Result(habit.color, getString(R.string.session_already_ended, habit.name), null, value = formats.elapsed(last.duration ?: 0L))
            } else {
                repository.log(habit.id, now)
                Result(habit.color, getString(R.string.session_starting, habit.name), null, stopwatch)
            }
        } else if (now - session.timestamp < Sessions.GRACE_MS) {
            Result(habit.color, getString(R.string.session_already_started, habit.name), null, stopwatch)
        } else {
            repository.finish(session.id, now)
            Result(habit.color, getString(R.string.session_ended, habit.name), null, value = formats.elapsed(now - session.timestamp))
        }
        Shortcuts.reportUsed(this, habit.id)
        // Its widget shows the live timer while a session runs.
        HabitWidgetProvider.updateFor(applicationContext, habit.id)
        return result
    }

    /** Runs on the I/O thread: logs [habit] at [now] and describes the confirmation. */
    private fun log(habit: Habit, now: Long): Result {
        val repository = HabitRepository.get(this)
        val entryId = repository.log(habit.id, now)

        val period = repository.settings.period(habit.id)
        val math = PeriodMath()
        val count = repository.count(habit.id, math.startOf(period, now), math.endOf(period, now))
        val label = getString(R.string.logged_label, habit.name, Formats(this).countPhrase(period, count))

        Shortcuts.reportUsed(this, habit.id)
        val suggest = habit.type == HabitType.COUNT && repository.settings.suggestTriggers(habit.id)
        return Result(
            habit.color,
            label,
            null,
            entryId = entryId,
            habitId = habit.id,
            suggestTriggers = suggest,
            triggers = if (suggest) repository.settings.triggers(habit.id) else emptyList(),
        )
    }

    /** A session running for hours was probably forgotten: finish it now, or discard it? */
    private fun askAboutLongSession(long: Outcome.LongRunning) {
        if (finishing) return
        val app = applicationContext
        dialog = AlertDialog.Builder(this, R.style.Theme_HabitTracker_Dialog)
            .setTitle(R.string.long_running_title)
            .setMessage(
                getString(R.string.long_running_message, long.habit.name, Formats(this).duration((long.now - long.session.timestamp) / 60_000.0)),
            )
            .setPositiveButton(R.string.finish_now) { _, _ ->
                Async.load({ toggleSession(long.habit, System.currentTimeMillis()) }, ::show)
            }
            .setNeutralButton(R.string.discard) { _, _ ->
                Async.io {
                    HabitRepository.get(app).deleteEntry(long.session.id)
                    HabitWidgetProvider.updateFor(app, long.habit.id)
                }
                finishQuietly()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> finishQuietly() }
            .setOnCancelListener { finishQuietly() }
            .show()
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
                Async.load({ log(habit, System.currentTimeMillis()) }, ::show)
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

        /** How far around the tapped widget a tap still counts as tapping it. */
        const val TAP_SLACK_DP = 8

        /** How long a picked trigger shows as chosen before the window closes. */
        const val PICKED_MS = 350L

        /** An ignored trigger suggestion closes by itself after this long. */
        const val PICKER_TIMEOUT_MS = 12_000L
    }
}
