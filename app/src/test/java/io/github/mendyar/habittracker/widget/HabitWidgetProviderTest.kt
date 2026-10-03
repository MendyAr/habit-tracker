package io.github.mendyar.habittracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.icons.HabitColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class HabitWidgetProviderTest {

    private lateinit var context: Context
    private lateinit var repository: HabitRepository
    private lateinit var manager: AppWidgetManager

    @Before
    fun setUp() {
        HabitRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        repository = HabitRepository.get(context)
        manager = AppWidgetManager.getInstance(context)
    }

    @After
    fun tearDown() {
        HabitRepository.resetForTests()
    }

    /** Places a widget on the (simulated) home screen, as the launcher would. */
    private fun placeWidget(): Int = shadowOf(manager).createWidget(HabitWidgetProvider::class.java, R.layout.widget_habit)

    /** The text the widget currently shows under its icon. */
    private fun label(widgetId: Int): String {
        val view = HabitWidgetProvider.views(context, widgetId).apply(context, FrameLayout(context))
        return view.findViewById<TextView>(R.id.widget_label).text.toString()
    }

    @Test
    fun aBoundWidgetShowsItsHabit() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        assertTrue(HabitWidgetProvider.bind(context, id, water.id))
        assertEquals("Water", label(id))
        assertEquals(id, HabitWidgetProvider.widgetFor(context, water.id))
    }

    @Test
    fun eachHabitHasAtMostOneWidget() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val first = placeWidget()
        val second = placeWidget()
        assertTrue(HabitWidgetProvider.bind(context, first, water.id))
        assertFalse(HabitWidgetProvider.bind(context, second, water.id))
        assertEquals(context.getString(R.string.widget_choose), label(second))
        // Re-binding a widget to its own habit (reconfiguring) is fine.
        assertTrue(HabitWidgetProvider.bind(context, first, water.id))
    }

    @Test
    fun anUnboundWidgetAsksForAHabitInsteadOfShowingTheAppIconHabit() {
        repository.ensureFirstRun()
        val id = placeWidget()
        assertEquals(context.getString(R.string.widget_choose), label(id))
    }

    @Test
    fun aWidgetIgnoresChangesOfTheAppIconHabit() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val walk = repository.createHabit("Walk", "walk", null, HabitColors.ALL[2])
        val id = placeWidget()
        HabitWidgetProvider.bind(context, id, water.id)
        repository.setDefaultHabit(walk.id)
        assertEquals("Water", label(id))
        repository.setDefaultHabit(null)
        assertEquals("Water", label(id))
    }

    @Test
    fun deletingAHabitMarksItsWidgetInsteadOfSwitchingToAnotherHabit() {
        repository.ensureFirstRun()
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        HabitWidgetProvider.bind(context, id, water.id)
        repository.deleteHabit(water.id)
        assertEquals(context.getString(R.string.widget_deleted), label(id))
        assertNull(HabitWidgetProvider.widgetFor(context, repository.defaultHabit()!!.id))
    }

    @Test
    fun aWidgetRequestedFromTheEditorIsBoundToThatHabit() {
        repository.ensureFirstRun()
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.settings.setPendingWidget(water.id)
        // The launcher places it; the provider binds it (from onUpdate or the pin callback).
        val id = placeWidget()
        HabitWidgetProvider.bindPending(context, id) // no-op if onUpdate already did it
        awaitCondition { HabitWidgetProvider.widgetFor(context, water.id) == id }
        assertEquals("Water", label(id))
        // The request is used up: the next new widget is not bound to it too.
        val next = placeWidget()
        assertFalse(HabitWidgetProvider.bindPending(context, next))
        assertEquals(context.getString(R.string.widget_choose), label(next))
    }

    @Test
    fun aStaleEditorRequestIsIgnored() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.settings.setPendingWidget(water.id, now = System.currentTimeMillis() - 10 * 60_000)
        assertFalse(HabitWidgetProvider.bindPending(context, placeWidget()))
    }

    @Test
    fun tappingAWidgetLogsItsHabitOnly() {
        repository.ensureFirstRun()
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        HabitWidgetProvider.bind(context, id, water.id)
        context.sendBroadcast(
            Intent(HabitWidgetProvider.ACTION_LOG)
                .setClass(context, HabitWidgetProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        )
        awaitCondition { repository.timestamps(water.id).size == 1 }
        assertEquals(0, repository.timestamps(repository.defaultHabit()!!.id).size)
    }

    @Test
    fun tappingAWidgetOfADeletedHabitLogsNothing() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        HabitWidgetProvider.bind(context, id, water.id)
        repository.deleteHabit(water.id)
        context.sendBroadcast(
            Intent(HabitWidgetProvider.ACTION_LOG)
                .setClass(context, HabitWidgetProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        )
        Thread.sleep(300)
        assertTrue(repository.habits().all { repository.timestamps(it.id).isEmpty() })
    }
}
