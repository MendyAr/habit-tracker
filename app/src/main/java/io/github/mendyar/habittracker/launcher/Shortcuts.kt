package io.github.mendyar.habittracker.launcher

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.log.LogActivity
import io.github.mendyar.habittracker.stats.StatsActivity

/**
 * Launcher integration:
 *  - pinned home-screen icons, one per habit, that log a timestamp when tapped;
 *  - dynamic shortcuts shown when the app icon is long-pressed (Android 7.1+),
 *    one "statistics" entry per habit, most recently logged first.
 */
object Shortcuts {
    const val ACTION_LOG = "io.github.mendyar.habittracker.action.LOG"
    const val ACTION_STATS = "io.github.mendyar.habittracker.action.STATS"
    const val EXTRA_HABIT_ID = "habit_id"

    private const val LEGACY_INSTALL = "com.android.launcher.action.INSTALL_SHORTCUT"

    /** Intent that logs a timestamp for [habitId] without opening any screen. */
    fun logIntent(context: Context, habitId: Long): Intent =
        Intent(ACTION_LOG).setClass(context, LogActivity::class.java).putExtra(EXTRA_HABIT_ID, habitId)

    /** Intent that opens the statistics of [habitId] from inside the app. */
    fun statsIntent(context: Context, habitId: Long): Intent =
        Intent(ACTION_STATS).setClass(context, StatsActivity::class.java).putExtra(EXTRA_HABIT_ID, habitId)

    /**
     * Intent behind the long-press "statistics" shortcut. It replaces whatever the
     * app's task showed before (e.g. an open editor), so the shortcut always lands
     * on the statistics it names.
     */
    fun statsShortcutIntent(context: Context, habitId: Long): Intent =
        statsIntent(context, habitId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun logShortcutId(habitId: Long) = "log_$habitId"

    fun statsShortcutId(habitId: Long) = "stats_$habitId"

    /**
     * Asks the launcher to add a home-screen icon for [habit]. On Android 8+ the
     * system shows a confirmation; older launchers receive the legacy broadcast.
     * Returns false when the launcher does not support pinning.
     */
    fun requestPin(context: Context, habit: Habit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!manager.isRequestPinShortcutSupported) return false
            return manager.requestPinShortcut(logShortcut(context, habit), null)
        }
        @Suppress("DEPRECATION")
        val legacy = Intent(LEGACY_INSTALL)
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, logIntent(context, habit.id))
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, habit.name)
            .putExtra(Intent.EXTRA_SHORTCUT_ICON, IconFactory.circleBitmap(context, habit, legacyIconSize(context)))
        context.sendBroadcast(legacy)
        return true
    }

    /**
     * Rebuilds the long-press shortcuts and refreshes the label and icon of any
     * pinned shortcuts. [habits] are ordered most relevant first.
     */
    fun sync(context: Context, habits: List<Habit>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        try {
            syncApi25(context, habits)
        } catch (e: IllegalStateException) {
            // Rate-limited while in the background or the user is locked; the next sync catches up.
        }
    }

    /** Disables the pinned and dynamic shortcuts of a deleted habit. */
    fun disable(context: Context, habitId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        manager.disableShortcuts(
            listOf(logShortcutId(habitId), statsShortcutId(habitId)),
            context.getString(R.string.shortcut_deleted),
        )
    }

    /** Tells the launcher a habit was used, so its predictions can learn. */
    fun reportUsed(context: Context, habitId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(logShortcutId(habitId))
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private fun syncApi25(context: Context, habits: List<Habit>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        // Launchers show at most four entries; the static "Habits" entry takes one.
        val slots = minOf(manager.maxShortcutCountPerActivity - 1, MAX_VISIBLE_DYNAMIC)
        val single = habits.size == 1
        val dynamic = habits.take(slots).mapIndexed { rank, habit -> statsShortcut(context, habit, rank, single) }
        manager.dynamicShortcuts = dynamic

        val pinnedIds = manager.pinnedShortcuts.map { it.id }.toSet()
        val updates = habits.flatMap { habit ->
            listOfNotNull(
                logShortcut(context, habit).takeIf { it.id in pinnedIds },
                statsShortcut(context, habit, 0, single).takeIf { it.id in pinnedIds },
            )
        }
        if (updates.isNotEmpty()) {
            manager.enableShortcuts(updates.map { it.id })
            manager.updateShortcuts(updates)
        }
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private fun logShortcut(context: Context, habit: Habit): ShortcutInfo =
        ShortcutInfo.Builder(context, logShortcutId(habit.id))
            .setShortLabel(habit.name)
            .setLongLabel(habit.name)
            .setIcon(icon(context, habit))
            .setIntent(logIntent(context, habit.id))
            .build()

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private fun statsShortcut(context: Context, habit: Habit, rank: Int, single: Boolean): ShortcutInfo =
        ShortcutInfo.Builder(context, statsShortcutId(habit.id))
            .setShortLabel(if (single) context.getString(R.string.shortcut_statistics) else habit.name)
            .setLongLabel(
                if (single) context.getString(R.string.shortcut_statistics)
                else context.getString(R.string.shortcut_statistics_of, habit.name),
            )
            .setIcon(icon(context, habit))
            .setIntent(statsShortcutIntent(context, habit.id))
            .setRank(rank)
            .build()

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private fun icon(context: Context, habit: Habit): Icon =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Icon.createWithAdaptiveBitmap(IconFactory.adaptiveBitmap(context, habit))
        } else {
            Icon.createWithBitmap(IconFactory.circleBitmap(context, habit, legacyIconSize(context)))
        }

    private fun legacyIconSize(context: Context): Int =
        (48 * context.resources.displayMetrics.density).toInt()

    private const val MAX_VISIBLE_DYNAMIC = 3
}
