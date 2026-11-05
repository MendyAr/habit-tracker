package io.github.mendyar.habittracker

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.widget.HabitWidgetProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Widgets on a real system: hosted by an AppWidgetHost exactly as a launcher
 * hosts them, so binding, rendering and taps go through the real AppWidget service.
 * Binding needs `adb shell appwidget grantbind --package io.github.mendyar.habittracker`
 * (done by .github/scripts/device-tests.sh).
 */
@RunWith(AndroidJUnit4::class)
class WidgetDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val provider = ComponentName(context, HabitWidgetProvider::class.java)
    private val host = AppWidgetHost(context, 7357)
    private val allocated = mutableListOf<Int>()
    private lateinit var repository: HabitRepository
    private lateinit var other: Habit

    @Before
    fun setUp() {
        HabitRepository.resetForTests()
        context.deleteDatabase("habits.db")
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        repository = HabitRepository.get(context)
        other = repository.createHabit("Walk", "walk", null, HabitColors.ALL[8])
        instrumentation.runOnMainSync { host.startListening() }
    }

    @After
    fun tearDown() {
        allocated.forEach { host.deleteAppWidgetId(it) }
        instrumentation.runOnMainSync { host.stopListening() }
    }

    /** Places a new widget, as a launcher does when the user drops one on the home screen. */
    private fun placeWidget(): Int {
        val id = host.allocateAppWidgetId()
        allocated += id
        assertTrue("Widget binding not granted (adb shell appwidget grantbind)", manager.bindAppWidgetIdIfAllowed(id, provider))
        return id
    }

    /** The label the hosted widget shows right now. */
    private fun label(id: Int): String {
        var text = ""
        instrumentation.runOnMainSync {
            val view = host.createView(context, id, manager.getAppWidgetInfo(id))
            text = view.findViewById<TextView>(R.id.widget_label)?.text?.toString().orEmpty()
        }
        return text
    }

    @Test
    fun aBoundWidgetShowsItsHabitAndATapLogsIt() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        assertTrue(onIo { HabitWidgetProvider.bind(context, id, water.id) })
        waitUntil { label(id) == "Water" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Tap the hosted widget exactly as a launcher would: it opens the logging window.
            instrumentation.runOnMainSync {
                host.createView(context, id, manager.getAppWidgetInfo(id)).findViewById<View>(R.id.widget_root).performClick()
            }
        } else {
            // Before Android 7 a host view that is not in a window cannot fire its click
            // PendingIntent, so start what the tap starts.
            context.startActivity(HabitWidgetProvider.tapIntent(context, water.id))
        }
        waitUntil { repository.timestamps(water.id).size == 1 }
        assertEquals(0, repository.timestamps(other.id).size)
    }

    @Test
    fun aDurationWidgetShowsALiveTimerWhileASessionRuns() {
        val breathing = repository.createHabit("Box Breathing", "box_breathing", null, HabitColors.ALL[3], HabitType.DURATION)
        val id = placeWidget()
        assertTrue(onIo { HabitWidgetProvider.bind(context, id, breathing.id) })
        waitUntil { label(id) == "Box Breathing" }

        repository.log(breathing.id, System.currentTimeMillis() - 65_000)
        onIo { HabitWidgetProvider.updateFor(context, breathing.id) }
        // "1:05" or "01:05", depending on the Android version.
        waitUntil { timer(id)?.matches(Regex("0?1:0\\d")) == true }
        // It counts up by itself.
        val first = timer(id)
        waitUntil(timeoutMs = 4_000) { timer(id) != first }
    }

    /** The live timer of the hosted widget, or null while it is hidden. */
    private fun timer(id: Int): String? {
        var text: String? = null
        instrumentation.runOnMainSync {
            val view = host.createView(context, id, manager.getAppWidgetInfo(id))
            val chronometer = view.findViewById<android.widget.Chronometer>(R.id.widget_timer)
            if (chronometer?.visibility == View.VISIBLE) {
                // A view outside a window doesn't tick: attach-time text is enough to compare.
                chronometer.measure(0, 0)
                text = chronometer.text?.toString()
            }
        }
        return text
    }

    @Test
    fun aHabitCannotGetASecondWidget() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val first = placeWidget()
        val second = placeWidget()
        assertTrue(onIo { HabitWidgetProvider.bind(context, first, water.id) })
        assertFalse(onIo { HabitWidgetProvider.bind(context, second, water.id) })
        waitUntil { label(second) == context.getString(R.string.widget_choose) }
    }

    @Test
    fun deletingTheHabitMarksItsWidget() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val id = placeWidget()
        onIo { HabitWidgetProvider.bind(context, id, water.id) }
        repository.deleteHabit(water.id)
        onIo { HabitWidgetProvider.updateAll(context) }
        waitUntil { label(id) == context.getString(R.string.widget_deleted) }
    }

    @Test
    fun aWidgetRequestedFromTheEditorGetsThatHabit() {
        val water = repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        repository.settings.setPendingWidget(water.id)
        val id = placeWidget()
        // Bound by the provider when the system announces the new widget.
        waitUntil { HabitWidgetProvider.widgetFor(context, water.id) == id }
        waitUntil { label(id) == "Water" }
    }

    /**
     * Runs [block] on the app's I/O thread, where the app itself binds and redraws
     * widgets, so it is serialised with the provider's own updates.
     */
    private fun <T> onIo(block: () -> T): T {
        var result: T? = null
        val done = CountDownLatch(1)
        Async.io {
            result = block()
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("Condition not met within $timeoutMs ms", condition())
    }
}
