package io.github.mendyar.habittracker

import android.os.Looper
import org.junit.Assert.fail
import org.robolectric.Shadows.shadowOf

/**
 * Waits for work on the app's background I/O thread: repeatedly drains the
 * (paused) main looper until [condition] holds or [timeoutMs] passes.
 */
fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(10)
    }
    fail("Condition not met within $timeoutMs ms")
}
