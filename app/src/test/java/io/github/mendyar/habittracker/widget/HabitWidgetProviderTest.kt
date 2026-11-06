package io.github.mendyar.habittracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.log.LogActivity
import io.github.mendyar.habittracker.ui.Async
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HabitWidgetProviderTest {

    private lateinit var context: Context
    private lateinit var repository: HabitRepository
    private lateinit var manager: AppWidgetManager

    @Before
    fun setUp() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        repository = HabitRepository.get(context)
        manager = AppWidgetManager.getInstance(context)
    }

    @After
    fun tearDown() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
    }

    /** Places a widget on the (simulated) home screen, as the launcher would. */
    private fun placeWidget(size: WidgetSize = WidgetSize.SMALL): Int =
        shadowOf(manager).createWidget(size.provider, R.layout.widget_habit).also {
            // Let the provider's onUpdate finish; Robolectric's widget manager is not thread-safe.
            Async.awaitIdle()
        }

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
    fun anUnboundWidgetAsksForAHabit() {
        repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        assertEquals(context.getString(R.string.widget_choose), label(id))
    }

    @Test
    fun deletingAHabitMarksItsWidgetInsteadOfSwitchingToAnotherHabit() {
        val walk = repository.createHabit("Walk", "walk", null, HabitColors.ALL[2])
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        HabitWidgetProvider.bind(context, id, water.id)
        repository.deleteHabit(water.id)
        assertEquals(context.getString(R.string.widget_deleted), label(id))
        assertNull(HabitWidgetProvider.widgetFor(context, walk.id))
    }

    @Test
    fun aWidgetRequestedFromTheEditorIsBoundToThatHabit() {
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
    fun tappingAWidgetOpensTheLoggingWindowForItsHabit() {
        repository.createHabit("Walk", "walk", null, HabitColors.ALL[2])
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val tap = HabitWidgetProvider.tapIntent(context, water.id)
        assertEquals(LogActivity::class.java.name, tap.component?.className)
        assertEquals(water.id, tap.getLongExtra(Shortcuts.EXTRA_HABIT_ID, -1))
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
    }

    @Test
    fun aTapOnAWidgetDrawnByAnOlderVersionRedrawsItWithoutLogging() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        HabitWidgetProvider.bind(context, id, water.id)
        context.sendBroadcast(
            Intent(HabitWidgetProvider.ACTION_LOG)
                .setClass(context, HabitWidgetProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
        Async.awaitIdle()
        assertEquals(0, repository.timestamps(water.id).size)
        assertEquals("Water", label(id))
    }

    @Test
    fun widgetsAddedInAnySizeAreTheSameWidget() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val large = placeWidget(WidgetSize.EXTRA_LARGE)
        val small = placeWidget()
        assertTrue(HabitWidgetProvider.bind(context, large, water.id))
        assertEquals("Water", label(large))
        assertEquals(large, HabitWidgetProvider.widgetFor(context, water.id))
        assertTrue(large in HabitWidgetProvider.widgetIds(context))
        // Still at most one widget per habit, whatever their sizes.
        assertFalse(HabitWidgetProvider.bind(context, small, water.id))
    }

    @Test
    fun sizedWidgetsAreAvailableOnAndroid9AndNewer() {
        assertTrue(HabitWidgetProvider.sizesAvailable(context))
        WidgetSize.entries.forEach { assertTrue(it.cells in 1..4) }
    }

    @Test
    @Config(sdk = [27])
    fun androidBefore9AddsTheStandardWidgetOnly() {
        assertFalse(HabitWidgetProvider.sizesAvailable(context))
    }

    @Test
    fun theEditorIsToldWhenTheLauncherPlacedTheRequestedWidget() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        var placed = -1L
        HabitWidgetProvider.onPinned = { placed = it }
        try {
            val id = placeWidget(WidgetSize.MEDIUM)
            context.sendBroadcast(
                Intent(HabitWidgetProvider.ACTION_PINNED)
                    .setClass(context, HabitWidgetProvider::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(Shortcuts.EXTRA_HABIT_ID, water.id),
            )
            awaitCondition { placed == water.id }
            assertEquals(id, HabitWidgetProvider.widgetFor(context, water.id))
        } finally {
            HabitWidgetProvider.onPinned = null
        }
    }

    @Test
    fun aDurationWidgetShowsALiveTimerWhileASessionRuns() {
        val breathing = repository.createHabit("Box breathing", "box_breathing", null, HabitColors.ALL[3], HabitType.DURATION)
        val id = placeWidget()
        HabitWidgetProvider.bind(context, id, breathing.id)
        fun shown() = HabitWidgetProvider.views(context, id).apply(context, FrameLayout(context))
        assertEquals(View.GONE, shown().findViewById<View>(R.id.widget_timer).visibility)

        repository.log(breathing.id, System.currentTimeMillis() - 65_000)
        val running = shown()
        assertEquals(View.VISIBLE, running.findViewById<View>(R.id.widget_timer).visibility)
        // The timer takes the name's place, so it fits even a 1 × 1 widget.
        assertEquals(View.GONE, running.findViewById<View>(R.id.widget_label).visibility)
    }
}
