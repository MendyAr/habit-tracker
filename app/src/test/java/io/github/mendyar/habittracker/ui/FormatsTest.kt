package io.github.mendyar.habittracker.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class FormatsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val formats = Formats(context)
    private val math = PeriodMath()

    @Test
    fun countPhrases() {
        assertEquals("1 today", formats.countPhrase(Period.DAY, 1))
        assertEquals("12 this week", formats.countPhrase(Period.WEEK, 12))
        assertEquals("3 this month", formats.countPhrase(Period.MONTH, 3))
        assertEquals("0 this year", formats.countPhrase(Period.YEAR, 0))
    }

    @Test
    fun relativeTitles() {
        val now = System.currentTimeMillis()
        assertEquals("Today", formats.periodTitle(Period.DAY, now, now))
        assertEquals("Yesterday", formats.periodTitle(Period.DAY, math.shift(Period.DAY, now, -1), now))
        assertEquals("This week", formats.periodTitle(Period.WEEK, now, now))
        assertEquals("Last week", formats.periodTitle(Period.WEEK, math.shift(Period.WEEK, now, -1), now))
        assertEquals("This month", formats.periodTitle(Period.MONTH, now, now))
        assertEquals("This year", formats.periodTitle(Period.YEAR, now, now))
    }

    @Test
    fun periodOffsets() {
        val now = System.currentTimeMillis()
        assertEquals(0, formats.periodOffset(Period.DAY, now, now))
        assertEquals(-1, formats.periodOffset(Period.MONTH, math.shift(Period.MONTH, now, -1), now))
        assertEquals(Int.MIN_VALUE, formats.periodOffset(Period.DAY, math.shift(Period.DAY, now, -10), now))
    }

    @Test
    fun numbers() {
        assertEquals("4.25", formats.number(4.25))
        assertEquals("3", formats.number(3.0))
        assertEquals("1,234", formats.number(1234))
    }
}
