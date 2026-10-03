package io.github.mendyar.habittracker.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Locale-aware labels for periods, dates and numbers. */
class Formats(private val context: Context) {
    private val locale: Locale = Locale.getDefault()
    private val math = PeriodMath(locale = locale)

    private fun pattern(skeleton: String) = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private val dayFormat = pattern("EEEMMMd")
    private val dayYearFormat = pattern("EEEMMMdyyyy")
    private val shortDayFormat = pattern("MMMd")
    private val shortDayYearFormat = pattern("MMMdyyyy")
    private val monthFormat = pattern("MMMMyyyy")
    private val shortMonthFormat = pattern("MMMyy")
    private val yearFormat = pattern("yyyy")
    private val decimal = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }

    /** "Today", "Yesterday", "This week", "October 2026" … for the period containing [millis]. */
    fun periodTitle(period: Period, millis: Long, now: Long = System.currentTimeMillis()): String {
        val offset = periodOffset(period, millis, now)
        return when (period) {
            Period.DAY -> when (offset) {
                0 -> context.getString(R.string.today)
                -1 -> context.getString(R.string.yesterday)
                else -> if (sameYear(millis, now)) dayFormat.format(Date(millis)) else dayYearFormat.format(Date(millis))
            }
            Period.WEEK -> when (offset) {
                0 -> context.getString(R.string.this_week)
                -1 -> context.getString(R.string.last_week)
                else -> weekRange(millis)
            }
            Period.MONTH -> when (offset) {
                0 -> context.getString(R.string.this_month)
                else -> monthFormat.format(Date(millis))
            }
            Period.YEAR -> when (offset) {
                0 -> context.getString(R.string.this_year)
                else -> yearFormat.format(Date(millis))
            }
        }
    }

    /** Lower-case phrase used after a count, e.g. "5 today" or "12 this week". */
    fun countPhrase(period: Period, count: Int): String {
        val res = when (period) {
            Period.DAY -> R.plurals.count_today
            Period.WEEK -> R.plurals.count_this_week
            Period.MONTH -> R.plurals.count_this_month
            Period.YEAR -> R.plurals.count_this_year
        }
        return context.resources.getQuantityString(res, count, count)
    }

    /** Detailed label for a chart bucket (shown when a bar is selected). */
    fun bucketLabel(period: Period, start: Long): String = when (period) {
        Period.DAY -> dayYearFormat.format(Date(start))
        Period.WEEK -> weekRange(start)
        Period.MONTH -> monthFormat.format(Date(start))
        Period.YEAR -> yearFormat.format(Date(start))
    }

    /** Compact axis label for a chart bucket. */
    fun axisLabel(period: Period, start: Long): String = when (period) {
        Period.DAY, Period.WEEK -> shortDayYearFormat.format(Date(start))
        Period.MONTH -> shortMonthFormat.format(Date(start))
        Period.YEAR -> yearFormat.format(Date(start))
    }

    /** Short date such as "Sep 3, 2026" used on range buttons. */
    fun shortDate(millis: Long): String = shortDayYearFormat.format(Date(millis))

    fun time(millis: Long): String = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

    /** Time with date, used for entries outside a single-day view. */
    fun dateTime(millis: Long): String =
        "${shortDayFormat.format(Date(millis))} · ${time(millis)}"

    fun number(value: Double): String = decimal.format(value)

    fun number(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value.toLong())

    /** Whole periods between the period of [millis] and that of [now] (negative = past). */
    fun periodOffset(period: Period, millis: Long, now: Long): Int {
        val target = math.startOf(period, millis)
        var cursor = math.startOf(period, now)
        var offset = 0
        // Small loop bounds: only "recent" offsets matter for labels.
        while (cursor > target && offset > -2) {
            cursor = math.shift(period, cursor, -1)
            offset--
        }
        return if (cursor == target) offset else Int.MIN_VALUE
    }

    private fun sameYear(a: Long, b: Long) = math.samePeriod(Period.YEAR, a, b)

    private fun weekRange(millis: Long): String {
        val start = math.startOf(Period.WEEK, millis)
        val end = math.endOf(Period.WEEK, millis) - 1
        return "${shortDayFormat.format(Date(start))} – ${shortDayYearFormat.format(Date(end))}"
    }
}
