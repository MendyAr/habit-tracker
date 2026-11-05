package io.github.mendyar.habittracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
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
) {

    /** All habits, oldest first. */
    fun habits(): List<Habit> = database.readableDatabase
        .query("habits", null, null, null, null, null, "_id")
        .use { c -> generateSequence { if (c.moveToNext()) c.toHabit() else null }.toList() }

    fun habit(id: Long): Habit? = database.readableDatabase
        .query("habits", null, "_id = ?", arrayOf(id.toString()), null, null, null)
        .use { c -> if (c.moveToFirst()) c.toHabit() else null }

    fun createHabit(name: String, icon: String, customIcon: String?, color: Int, type: HabitType = HabitType.COUNT): Habit {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name)
            put("icon", icon)
            put("custom_icon", customIcon)
            put("color", color)
            put("created_at", now)
            put("type", type.ordinal)
        }
        val id = database.writableDatabase.insertOrThrow("habits", null, values)
        return Habit(id, name, icon, customIcon, color, now, type)
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

    /**
     * The entry that makes logging [habitId] at [at] look like a mistake: one in the
     * last [DUPLICATE_WINDOW_MS], or null when there is none or the user turned the
     * question off for this habit.
     */
    fun recentDuplicate(habitId: Long, at: Long): Long? {
        if (!settings.confirmDuplicates(habitId)) return null
        return database.readableDatabase
            .rawQuery(
                "SELECT MAX(ts) FROM entries WHERE habit_id = ? AND ts > ? AND ts <= ?",
                arrayOf(habitId.toString(), (at - DUPLICATE_WINDOW_MS).toString(), at.toString()),
            )
            .use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }

    /** Whether [habitId] already has an entry in the same minute as [timestamp] (manual entries). */
    fun hasEntryInMinute(habitId: Long, timestamp: Long): Boolean {
        val minute = timestamp - Math.floorMod(timestamp, MINUTE_MS)
        return count(habitId, minute, minute + MINUTE_MS) > 0
    }

    /** The running session of a duration habit (started, not finished), if any. */
    fun running(habitId: Long): Entry? = database.readableDatabase
        .rawQuery(
            "SELECT _id, ts FROM entries WHERE habit_id = ? AND duration IS NULL ORDER BY ts DESC LIMIT 1",
            arrayOf(habitId.toString()),
        )
        .use { c -> if (c.moveToFirst()) Entry(c.getLong(0), habitId, c.getLong(1)) else null }

    /** The finished session of [habitId] that ended last, if any. */
    fun lastFinished(habitId: Long): Entry? = database.readableDatabase
        .rawQuery(
            "SELECT _id, ts, duration FROM entries WHERE habit_id = ? AND duration IS NOT NULL ORDER BY ts + duration DESC LIMIT 1",
            arrayOf(habitId.toString()),
        )
        .use { c -> if (c.moveToFirst()) Entry(c.getLong(0), habitId, c.getLong(1), c.getLong(2)) else null }

    /** Finishes the session [entryId] at [end], recording its duration. */
    fun finish(entryId: Long, end: Long) {
        database.writableDatabase.execSQL(
            "UPDATE entries SET duration = MAX(0, ? - ts) WHERE _id = ?",
            arrayOf(end, entryId),
        )
    }

    /** Records a finished session of [habitId] (e.g. one added by hand). */
    fun addSession(habitId: Long, start: Long, duration: Long): Long {
        val values = ContentValues().apply {
            put("habit_id", habitId)
            put("ts", start)
            put("duration", duration)
        }
        return database.writableDatabase.insertOrThrow("entries", null, values)
    }

    /** The finished sessions of the duration habit [habitId], oldest first. */
    fun sessions(habitId: Long): List<Entry> = database.readableDatabase
        .rawQuery(
            "SELECT _id, ts, duration FROM entries WHERE habit_id = ? AND duration IS NOT NULL ORDER BY ts",
            arrayOf(habitId.toString()),
        )
        .use { c -> generateSequence { if (c.moveToNext()) Entry(c.getLong(0), habitId, c.getLong(1), c.getLong(2)) else null }.toList() }

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
            "SELECT _id, ts, duration, trigger_tag FROM entries WHERE habit_id = ? AND ts >= ? AND ts < ? ORDER BY ts DESC LIMIT $limit",
            arrayOf(habitId.toString(), from.toString(), to.toString()),
        )
        .use { c ->
            generateSequence {
                if (c.moveToNext()) {
                    Entry(c.getLong(0), habitId, c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getString(3))
                } else {
                    null
                }
            }.toList()
        }

    /** Tags the entry [entryId] with what set it off; null removes the tag. */
    fun setTrigger(entryId: Long, trigger: String?) {
        val values = ContentValues().apply { if (trigger == null) putNull("trigger_tag") else put("trigger_tag", trigger) }
        database.writableDatabase.update("entries", values, "_id = ?", arrayOf(entryId.toString()))
    }

    /** The time and trigger of every tagged entry of [habitId], oldest first. */
    fun taggedEntries(habitId: Long): List<Pair<Long, String>> = database.readableDatabase
        .rawQuery(
            "SELECT ts, trigger_tag FROM entries WHERE habit_id = ? AND trigger_tag IS NOT NULL ORDER BY ts",
            arrayOf(habitId.toString()),
        )
        .use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) to c.getString(1) else null }.toList() }

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
        type = HabitType.entries.getOrElse(getInt(getColumnIndexOrThrow("type"))) { HabitType.COUNT },
    )

    companion object {
        /** A second log of the same habit within this time asks first (see [recentDuplicate]). */
        const val DUPLICATE_WINDOW_MS = 60_000L
        private const val MINUTE_MS = 60_000L

        @Volatile
        private var instance: HabitRepository? = null

        /** Process-wide repository backed by the app's database. */
        fun get(context: Context): HabitRepository = instance ?: synchronized(this) {
            instance ?: HabitRepository(HabitDatabase(context), Settings(context)).also { instance = it }
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
