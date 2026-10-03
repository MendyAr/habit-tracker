package io.github.mendyar.habittracker.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Minimal background execution: one serial I/O thread, so database writes are
 * applied in the order they were requested, plus a main-thread handler.
 */
object Async {
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "habit-io").apply { isDaemon = true }
    }

    /** Looked up on each use, so it always targets the current main looper. */
    private val main: Handler get() = Handler(Looper.getMainLooper())

    /** Runs [work] on the I/O thread. */
    fun io(work: () -> Unit) {
        io.execute(work)
    }

    /** Runs [work] on the I/O thread and delivers its result to [onResult] on the main thread. */
    fun <T> load(work: () -> T, onResult: (T) -> Unit) {
        io.execute {
            val result = work()
            main.post { onResult(result) }
        }
    }

    fun main(work: () -> Unit) {
        main.post(work)
    }

    fun mainDelayed(delayMs: Long, work: () -> Unit) {
        main.postDelayed(work, delayMs)
    }

    /** Blocks until all I/O work queued so far has run. For tests. */
    fun awaitIdle() {
        io.submit {}.get(10, TimeUnit.SECONDS)
    }
}
