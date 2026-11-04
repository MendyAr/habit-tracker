package io.github.mendyar.habittracker.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.mendyar.habittracker.data.HabitRepository

/**
 * Decides whether opening the app should first show the tutorial (first install)
 * or "What's new" (after an update with user-visible changes), once each.
 */
object Onboarding {

    enum class Mode { TUTORIAL, WHATS_NEW }

    /**
     * Versions whose "What's new" page has content, as version codes. A release
     * with user-visible changes adds its code here and its items to
     * `whats_new_items`.
     */
    private val WHATS_NEW_VERSIONS = setOf(5)

    /** What to show now, or null. Runs database I/O: call it off the main thread. */
    fun pending(context: Context): Mode? {
        val repository = HabitRepository.get(context)
        val seen = repository.settings.lastSeenVersion
        val current = versionCode(context).coerceAtLeast(1)
        return when {
            // Never opened since this was introduced: a fresh install, or an update from 1.3 or older.
            seen == 0 -> if (repository.habits().isEmpty()) Mode.TUTORIAL else Mode.WHATS_NEW
            seen < current && WHATS_NEW_VERSIONS.any { it in (seen + 1)..current } -> Mode.WHATS_NEW
            else -> null
        }.also { if (seen != current) repository.settings.lastSeenVersion = current }
    }

    fun versionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }
}
