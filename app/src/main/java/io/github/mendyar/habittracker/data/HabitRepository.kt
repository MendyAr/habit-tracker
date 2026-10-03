package io.github.mendyar.habittracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.icons.HabitIcons
import java.io.File

/**
 * Single access point for habits and their timestamps.
 *
 * All methods do blocking database I/O; call them off the main thread
 * (see [io.github.mendyar.habittracker.ui.Async]).
 */
class HabitRepository(
    private val database: HabitDatabase,
    val settings: Settings,
    private val defaultHabitName: String,
) {

    /** All habits, oldest first. */
    fun habits(): List<Habit> = database.readableDatabase
        .query("habits", null, null, null, null, null, "_id")
        .use { c -> generateSequence { if (c.moveToNext()) c.toHabit() else null }.toList() }

    fun habit(id: Long): Habit? = database.readableDatabase
        .query("habits", null, "_id = ?", arrayOf(id.toString()), null, null, null)
        .use { c -> if (c.moveToFirst()) c.toHabit() else null }

    /** The habit logged by the main launcher icon, or null when the user chose none. */
    fun defaultHabit(): Habit? = habit(settings.defaultHabitId)

    /** Makes [id] the habit the main launcher icon logs; null for none. */
    fun setDefaultHabit(id: Long?) {
        settings.defaultHabitId = id ?: -1
    }

    /**
     * On the very first run, creates one habit for the app icon to log, so the app
     * works straight after installation. Never creates anything afterwards, even
     * if the user deletes every habit.
     */
    @Synchronized
    fun ensureFirstRun() {
        if (settings.initialized) return
        if (habits().isEmpty()) {
            val first = createHabit(defaultHabitName, HabitIcons.DEFAULT, null, HabitColors.DEFAULT)
            settings.defaultHabitId = first.id
        }
        settings.initialized = true
    }

    fun createHabit(name: String, icon: String, customIcon: String?, color: Int): Habit {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name)
            put("icon", icon)
            put("custom_icon", customIcon)
            put("color", color)
            put("created_at", now)
        }
        val id = database.writableDatabase.insertOrThrow("habits", null, values)
        return Habit(id, name, icon, customIcon, color, now)
    }

    fun updateHabit(habit: Habit) {
        val old = habit(habit.id)
        val values = ContentValues().apply {
            put("name", habit.name)
            put("icon", habit.icon)
            put("custom_icon", habit.customIcon)
            put("color", habit.color)
        }
        database.writableDatabase.update("habits", values, "_id = ?", arrayOf(habit.id.toString()))
        if (old?.customIcon != null && old.customIcon != habit.customIcon) File(old.customIcon).delete()
    }

    /** Deletes [id] together with all its timestamps and its custom icon file. */
    fun deleteHabit(id: Long) {
        val old = habit(id) ?: return
        database.writableDatabase.delete("habits", "_id = ?", arrayOf(id.toString()))
        old.customIcon?.let { File(it).delete() }
        settings.forgetHabit(id)
        if (settings.defaultHabitId == id) settings.defaultHabitId = -1
    }

    /** Deletes every timestamp of [habitId]; the habit itself stays. */
    fun clearEntries(habitId: Long) {
        database.writableDatabase.delete("entries", "habit_id = ?", arrayOf(habitId.toString()))
    }

    /** Records a timestamp for [habitId] and returns the new entry id. */
    fun log(habitId: Long, timestamp: Long = System.currentTimeMillis()): Long {
        val values = ContentValues().apply {
            put("habit_id", habitId)
            put("ts", timestamp)
        }
        return database.writableDatabase.insertOrThrow("entries", null, values)
    }

    fun deleteEntry(entryId: Long) {
        database.writableDatabase.delete("entries", "_id = ?", arrayOf(entryId.toString()))
    }

    /** Every timestamp of [habitId], ascending. */
    fun timestamps(habitId: Long): LongArray = database.readableDatabase
        .rawQuery("SELECT ts FROM entries WHERE habit_id = ? ORDER BY ts", arrayOf(habitId.toString()))
        .use { c ->
            LongArray(c.count) { i ->
                c.moveToPosition(i)
                c.getLong(0)
            }
        }

    /** Number of timestamps of [habitId] in [[from], [to]). */
    fun count(habitId: Long, from: Long, to: Long): Int = database.readableDatabase
        .rawQuery(
            "SELECT COUNT(*) FROM entries WHERE habit_id = ? AND ts >= ? AND ts < ?",
            arrayOf(habitId.toString(), from.toString(), to.toString()),
        )
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Entries of [habitId] in [[from], [to]), newest first, at most [limit]. */
    fun entries(habitId: Long, from: Long, to: Long, limit: Int): List<Entry> = database.readableDatabase
        .rawQuery(
            "SELECT _id, ts FROM entries WHERE habit_id = ? AND ts >= ? AND ts < ? ORDER BY ts DESC LIMIT $limit",
            arrayOf(habitId.toString(), from.toString(), to.toString()),
        )
        .use { c -> generateSequence { if (c.moveToNext()) Entry(c.getLong(0), habitId, c.getLong(1)) else null }.toList() }

    /** Time of the most recent entry per habit id (habits never logged are absent). */
    fun lastLogged(): Map<Long, Long> = database.readableDatabase
        .rawQuery("SELECT habit_id, MAX(ts) FROM entries GROUP BY habit_id", null)
        .use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) to c.getLong(1) else null }.toMap() }

    private fun Cursor.toHabit() = Habit(
        id = getLong(getColumnIndexOrThrow("_id")),
        name = getString(getColumnIndexOrThrow("name")),
        icon = getString(getColumnIndexOrThrow("icon")),
        customIcon = getString(getColumnIndexOrThrow("custom_icon")),
        color = getInt(getColumnIndexOrThrow("color")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
    )

    companion object {
        @Volatile
        private var instance: HabitRepository? = null

        /** Process-wide repository backed by the app's database. */
        fun get(context: Context): HabitRepository = instance ?: synchronized(this) {
            instance ?: HabitRepository(
                HabitDatabase(context),
                Settings(context),
                context.getString(R.string.default_habit_name),
            ).also { instance = it }
        }

        /** Drops the process-wide instance; tests use this to start from a clean database. */
        fun resetForTests() {
            synchronized(this) {
                instance?.database?.close()
                instance = null
            }
        }
    }
}
