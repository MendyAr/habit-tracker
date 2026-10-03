package io.github.mendyar.habittracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.core.DateRange
import io.github.mendyar.habittracker.core.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /** A fresh instance over the same file, as after an app restart. */
    private fun reopen() = Settings(context)

    @Test
    fun defaultsBeforeAnythingIsStored() {
        val settings = Settings(context)
        assertEquals(Period.DAY, settings.period(1))
        assertEquals(DateRange.ALL, settings.range(1))
        assertFalse(settings.hintShown)
        assertEquals(-1L, settings.defaultHabitId)
    }

    @Test
    fun periodAndRangeArePerHabitAndSurviveRestart() {
        Settings(context).apply {
            setPeriod(1, Period.WEEK)
            setPeriod(2, Period.YEAR)
            setRange(1, DateRange(fromDay = 1_000, toDay = null))
            setRange(2, DateRange(fromDay = 5, toDay = 9))
            hintShown = true
        }
        val settings = reopen()
        assertEquals(Period.WEEK, settings.period(1))
        assertEquals(Period.YEAR, settings.period(2))
        assertEquals(DateRange(1_000, null), settings.range(1))
        assertEquals(DateRange(5, 9), settings.range(2))
        assertTrue(settings.hintShown)
    }

    @Test
    fun widgetsMapToHabits() {
        val settings = Settings(context)
        settings.setWidgetHabit(42, 7)
        assertEquals(7L, reopen().widgetHabit(42))
        settings.removeWidget(42)
        assertEquals(-1L, reopen().widgetHabit(42))
    }
}
