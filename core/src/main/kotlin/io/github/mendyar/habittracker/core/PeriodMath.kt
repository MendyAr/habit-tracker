package io.github.mendyar.habittracker.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar arithmetic for [Period]s in a fixed time zone.
 *
 * All values are epoch milliseconds. A period "start" is local midnight of the
 * first day of the period (the locale's first day of the week for [Period.WEEK]).
 *
 * Instances wrap a mutable [Calendar] and are therefore not thread-safe.
 */
class PeriodMath(
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
    firstDayOfWeek: Int? = null,
) {
    private val cal: Calendar = Calendar.getInstance(timeZone, locale).apply {
        if (firstDayOfWeek != null) this.firstDayOfWeek = firstDayOfWeek
    }

    /** Start of the [period] that contains [millis]. */
    fun startOf(period: Period, millis: Long): Long {
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (period) {
            Period.DAY -> Unit
            Period.WEEK -> {
                val back = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
                cal.add(Calendar.DAY_OF_MONTH, -back)
            }
            Period.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
            Period.YEAR -> cal.set(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** Start of the period [amount] periods away from the one containing [millis]. */
    fun shift(period: Period, millis: Long, amount: Int): Long {
        cal.timeInMillis = startOf(period, millis)
        when (period) {
            Period.DAY -> cal.add(Calendar.DAY_OF_MONTH, amount)
            Period.WEEK -> cal.add(Calendar.DAY_OF_MONTH, amount * 7)
            Period.MONTH -> cal.add(Calendar.MONTH, amount)
            Period.YEAR -> cal.add(Calendar.YEAR, amount)
        }
        // Re-normalise: around DST changes local midnight can land on 01:00.
        return startOf(period, cal.timeInMillis)
    }

    /** Exclusive end of the period containing [millis]. */
    fun endOf(period: Period, millis: Long): Long = shift(period, millis, 1)

    /** Whether [a] and [b] fall into the same [period]. */
    fun samePeriod(period: Period, a: Long, b: Long): Boolean = startOf(period, a) == startOf(period, b)
}
