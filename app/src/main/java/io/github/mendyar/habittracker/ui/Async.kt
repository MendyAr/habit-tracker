package io.github.mendyar.habittracker.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Minimal background execution: one serial I/O thread, so database writes are
 * applied in the order they were requested, plus a main-thread handler.
 */
object Async {
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "habit-io").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

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
}
