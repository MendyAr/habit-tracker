package io.github.mendyar.habittracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.icons.HabitColors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HabitRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        // A null name gives an in-memory database.
        repository = HabitRepository(HabitDatabase(context, null), Settings(prefs))
    }

    @Test
    fun aFreshInstallStartsWithoutHabits() {
        // Nothing is created implicitly: the user adds their first habit.
        assertTrue(repository.habits().isEmpty())
    }

    @Test
    fun clearEntriesKeepsTheHabitAndOtherHabitsEntries() {
        val a = repository.createHabit("A", "check", null, HabitColors.DEFAULT)
        val b = repository.createHabit("B", "check", null, HabitColors.DEFAULT)
        repeat(3) { repository.log(a.id, it.toLong()) }
        repository.log(b.id, 5)
        repository.clearEntries(a.id)
        assertEquals(0, repository.timestamps(a.id).size)
        assertEquals(1, repository.timestamps(b.id).size)
        assertEquals(a, repository.habit(a.id))
    }

    @Test
    fun logAndQueryTimestamps() {
        val habit = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        repository.log(habit.id, 300)
        repository.log(habit.id, 100)
        repository.log(habit.id, 200)
        assertArrayEquals(longArrayOf(100, 200, 300), repository.timestamps(habit.id))
        assertEquals(2, repository.count(habit.id, 100, 300))
        assertEquals(listOf(300L, 200L), repository.entries(habit.id, 0, 1000, limit = 2).map { it.timestamp })
    }

    @Test
    fun habitsKeepSeparateTimestamps() {
        val a = repository.createHabit("A", "check", null, HabitColors.DEFAULT)
        val b = repository.createHabit("B", "check", null, HabitColors.DEFAULT)
        repository.log(a.id, 1)
        repository.log(b.id, 2)
        repository.log(b.id, 3)
        assertEquals(1, repository.timestamps(a.id).size)
        assertEquals(2, repository.timestamps(b.id).size)
        assertEquals(mapOf(a.id to 1L, b.id to 3L), repository.lastLogged())
    }

    @Test
    fun deleteEntryRemovesOnlyThatEntry() {
        val habit = repository.createHabit("A", "check", null, HabitColors.DEFAULT)
        val keep = repository.log(habit.id, 10)
        val drop = repository.log(habit.id, 20)
        repository.deleteEntry(drop)
        assertEquals(listOf(keep), repository.entries(habit.id, 0, 100, 10).map { it.id })
    }

    @Test
    fun deletingAHabitCascadesToItsEntriesAndSettings() {
        val habit = repository.createHabit("A", "check", null, HabitColors.DEFAULT)
        repository.log(habit.id, 10)
        repository.settings.setPeriod(habit.id, io.github.mendyar.habittracker.core.Period.MONTH)
        repository.deleteHabit(habit.id)
        assertNull(repository.habit(habit.id))
        assertEquals(0, repository.timestamps(habit.id).size)
        assertEquals(io.github.mendyar.habittracker.core.Period.DAY, repository.settings.period(habit.id))
    }

    @Test
    fun updateHabitPersistsChangesAndRemovesReplacedCustomIcon() {
        val oldIcon = File(context.filesDir, "old.png").apply { writeText("x") }
        val habit = repository.createHabit("Habit", "check", oldIcon.path, HabitColors.DEFAULT)
        val updated = habit.copy(name = "Smoking tracker", icon = "smoking", customIcon = null, color = HabitColors.ALL[5])
        repository.updateHabit(updated)
        assertEquals(updated, repository.habit(habit.id))
        assertTrue(!oldIcon.exists())
    }

    @Test
    fun recentDuplicatesAreFoundWithinAMinuteUnlessTurnedOff() {
        val habit = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val other = repository.createHabit("Walk", "walk", null, HabitColors.ALL[2])
        val now = 1_000_000_000L
        repository.log(habit.id, now - 30_000)
        assertEquals(now - 30_000, repository.recentDuplicate(habit.id, now))
        assertNull(repository.recentDuplicate(other.id, now))
        assertNull(repository.recentDuplicate(habit.id, now + HabitRepository.DUPLICATE_WINDOW_MS))
        repository.settings.setConfirmDuplicates(habit.id, false)
        assertNull(repository.recentDuplicate(habit.id, now))
    }

    @Test
    fun manualEntriesInTheSameMinuteAreDetected() {
        val habit = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val minute = 1_000_020_000L // a whole minute
        repository.log(habit.id, minute + 15_000)
        assertTrue(repository.hasEntryInMinute(habit.id, minute))
        assertTrue(repository.hasEntryInMinute(habit.id, minute + 59_000))
        assertFalse(repository.hasEntryInMinute(habit.id, minute + 60_000))
        assertFalse(repository.hasEntryInMinute(habit.id, minute - 1))
    }

    @Test
    fun durationSessionsStartRunAndFinish() {
        val breathing = repository.createHabit("Box breathing", "box_breathing", null, HabitColors.ALL[3], HabitType.DURATION)
        assertEquals(HabitType.DURATION, repository.habit(breathing.id)?.type)
        val start = 1_000_000_000L
        repository.log(breathing.id, start)
        val running = repository.running(breathing.id)!!
        assertEquals(start, running.timestamp)
        assertNull(running.duration)

        repository.finish(running.id, start + 272_000)
        assertNull(repository.running(breathing.id))
        assertEquals(listOf(272_000L), repository.sessions(breathing.id).map { it.duration })

        repository.addSession(breathing.id, start + 3_600_000, 180_000)
        assertEquals(2, repository.sessions(breathing.id).size)
    }

    @Test
    fun upgradingFromVersion1KeepsHabitsAndEntriesAsCountData() {
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE habits (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, icon TEXT NOT NULL, custom_icon TEXT, color INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE entries (_id INTEGER PRIMARY KEY AUTOINCREMENT, habit_id INTEGER NOT NULL REFERENCES habits(_id) ON DELETE CASCADE, ts INTEGER NOT NULL)")
            db.execSQL("INSERT INTO habits (name, icon, color, created_at) VALUES ('Smoking', 'smoking', 1, 5)")
            db.execSQL("INSERT INTO entries (habit_id, ts) VALUES (1, 100)")
            db.version = 1
        }
        val prefs = context.getSharedPreferences("upgrade", Context.MODE_PRIVATE)
        val upgraded = HabitRepository(HabitDatabase(context, name), Settings(prefs))
        val habit = upgraded.habits().single()
        assertEquals(HabitType.COUNT, habit.type)
        assertArrayEquals(longArrayOf(100), upgraded.timestamps(habit.id))
        assertNull(upgraded.running(habit.id)?.duration)
        context.deleteDatabase(name)
    }

    @Test
    fun upgradingFromVersion2KeepsSessionsAndAddsTriggers() {
        val name = "upgrade-v2-test.db"
        context.deleteDatabase(name)
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE habits (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, icon TEXT NOT NULL, custom_icon TEXT, color INTEGER NOT NULL, created_at INTEGER NOT NULL, type INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE entries (_id INTEGER PRIMARY KEY AUTOINCREMENT, habit_id INTEGER NOT NULL REFERENCES habits(_id) ON DELETE CASCADE, ts INTEGER NOT NULL, duration INTEGER)")
            db.execSQL("INSERT INTO habits (name, icon, color, created_at, type) VALUES ('Breathing', 'box_breathing', 1, 5, 1)")
            db.execSQL("INSERT INTO entries (habit_id, ts, duration) VALUES (1, 100, 60000)")
            db.version = 2
        }
        val prefs = context.getSharedPreferences("upgrade-v2", Context.MODE_PRIVATE)
        val upgraded = HabitRepository(HabitDatabase(context, name), Settings(prefs))
        val habit = upgraded.habits().single()
        assertEquals(HabitType.DURATION, habit.type)
        assertEquals(60_000L, upgraded.sessions(habit.id).single().duration)
        val entry = upgraded.entries(habit.id, 0, 1_000, 10).single()
        assertNull(entry.trigger)
        upgraded.setTrigger(entry.id, "Stress")
        assertEquals("Stress", upgraded.entries(habit.id, 0, 1_000, 10).single().trigger)
        context.deleteDatabase(name)
    }

    @Test
    fun entriesCanBeTaggedWithATrigger() {
        val smoking = repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        val first = repository.log(smoking.id, 100)
        val second = repository.log(smoking.id, 200)
        repository.log(smoking.id, 300)
        repository.setTrigger(first, "Coffee")
        repository.setTrigger(second, "Coffee")
        assertEquals(listOf(100L to "Coffee", 200L to "Coffee"), repository.taggedEntries(smoking.id))
        repository.setTrigger(second, null)
        assertEquals(listOf(100L to "Coffee"), repository.taggedEntries(smoking.id))
    }

    @Test
    fun eachHabitOffersThePresetTriggersUntilChangedAndCanTurnThemOff() {
        val prefs = context.getSharedPreferences("triggers", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        val settings = Settings(prefs)
        assertEquals(Triggers.PRESETS, settings.triggers(1))
        assertTrue(settings.suggestTriggers(1))
        settings.setTriggers(1, listOf("Phone Call", "Coffee"))
        settings.setSuggestTriggers(2, false)
        assertEquals(listOf("Phone Call", "Coffee"), settings.triggers(1))
        assertEquals(Triggers.PRESETS, settings.triggers(2))
        assertFalse(settings.suggestTriggers(2))
        settings.setTriggers(1, emptyList())
        assertEquals(emptyList<String>(), settings.triggers(1))
        settings.forgetHabit(1)
        assertEquals(Triggers.PRESETS, settings.triggers(1))
    }

    @Test
    fun typedTriggersAreTidied() {
        assertEquals("Phone Call", Triggers.clean("  Phone\n  Call "))
        assertNull(Triggers.clean("   "))
        assertTrue(Triggers.clean("A very long trigger name that goes on and on")!!.length <= Triggers.MAX_LENGTH)
        assertEquals(listOf("Coffee", "Tea"), Triggers.added(listOf("Coffee"), "Tea"))
        assertEquals(listOf("Coffee"), Triggers.added(listOf("Coffee"), "coffee"))
    }
}
