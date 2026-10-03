package io.github.mendyar.habittracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.icons.HabitColors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        repository = HabitRepository(HabitDatabase(context, null), Settings(prefs), "Habit")
    }

    @Test
    fun defaultHabitIsCreatedOnceAndRemembered() {
        val first = repository.defaultHabit()
        assertEquals("Habit", first.name)
        assertEquals(first, repository.defaultHabit())
        assertEquals(1, repository.habits().size)
        assertEquals(first.id, repository.settings.defaultHabitId)
    }

    @Test
    fun deletingTheDefaultHabitFallsBackToTheOldestRemaining() {
        val first = repository.defaultHabit()
        val second = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.deleteHabit(first.id)
        assertEquals(second.id, repository.defaultHabit().id)
    }

    @Test
    fun deletingTheOnlyHabitRecreatesADefault() {
        val first = repository.defaultHabit()
        repository.deleteHabit(first.id)
        val recreated = repository.defaultHabit()
        assertNotEquals(first.id, recreated.id)
        assertEquals(1, repository.habits().size)
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
    fun habitOrDefaultHandlesUnknownIds() {
        val default = repository.defaultHabit()
        assertEquals(default.id, repository.habitOrDefault(-1).id)
        assertEquals(default.id, repository.habitOrDefault(999).id)
    }
}
