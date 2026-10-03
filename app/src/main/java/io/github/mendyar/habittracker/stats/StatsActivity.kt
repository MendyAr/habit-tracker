package io.github.mendyar.habittracker.stats

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.Bucket
import io.github.mendyar.habittracker.core.DateRange
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.data.Entry
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitEditActivity
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.Formats
import io.github.mendyar.habittracker.ui.toast
import io.github.mendyar.habittracker.ui.visible
import java.util.Calendar

/**
 * Statistics of one habit: the count of a chosen day/week/month/year, average,
 * min, max, standard deviation and variance per period, a bar chart over a
 * user-defined window, and the individual entries (which can be added or removed).
 *
 * The chosen period and chart window are remembered per habit.
 */
class StatsActivity : Activity() {

    private lateinit var repository: HabitRepository
    private lateinit var formats: Formats
    private val math = PeriodMath()

    private var requestedHabitId = -1L
    private var habit: Habit? = null
    private var habits: List<Habit> = emptyList()
    private var timestamps = LongArray(0)
    private var period = Period.DAY
    private var range = DateRange.ALL
    private var buckets: List<Bucket> = emptyList()

    /** Any instant inside the period whose count is shown. */
    private var selected = System.currentTimeMillis()

    private lateinit var habitIcon: ImageView
    private lateinit var habitName: TextView
    private lateinit var periodButtons: Map<Period, TextView>
    private lateinit var selectedLabel: TextView
    private lateinit var selectedCount: TextView
    private lateinit var selectedCaption: TextView
    private lateinit var nextButton: ImageButton
    private lateinit var statsTitle: TextView
    private lateinit var statsCaption: TextView
    private lateinit var rangeAll: TextView
    private lateinit var rangeFrom: TextView
    private lateinit var rangeTo: TextView
    private lateinit var chart: BarChartView
    private lateinit var entriesTitle: TextView
    private lateinit var entriesList: LinearLayout
    private lateinit var entriesFooter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        repository = HabitRepository.get(this)
        formats = Formats(this)

        habitIcon = findViewById(R.id.habit_icon)
        habitName = findViewById(R.id.habit_name)
        selectedLabel = findViewById(R.id.selected_label)
        selectedCount = findViewById(R.id.selected_count)
        selectedCaption = findViewById(R.id.selected_caption)
        nextButton = findViewById(R.id.next_button)
        statsTitle = findViewById(R.id.stats_title)
        statsCaption = findViewById(R.id.stats_caption)
        rangeAll = findViewById(R.id.range_all)
        rangeFrom = findViewById(R.id.range_from)
        rangeTo = findViewById(R.id.range_to)
        chart = findViewById(R.id.chart)
        entriesTitle = findViewById(R.id.entries_title)
        entriesList = findViewById(R.id.entries_list)
        entriesFooter = findViewById(R.id.entries_footer)
        periodButtons = mapOf(
            Period.DAY to findViewById(R.id.period_day),
            Period.WEEK to findViewById(R.id.period_week),
            Period.MONTH to findViewById(R.id.period_month),
            Period.YEAR to findViewById(R.id.period_year),
        )

        periodButtons.forEach { (p, button) -> button.setOnClickListener { changePeriod(p) } }
        findViewById<View>(R.id.habit_switcher).setOnClickListener(::showHabitMenu)
        findViewById<View>(R.id.edit_button).setOnClickListener {
            habit?.let { startActivity(HabitEditActivity.intent(this, it.id)) }
        }
        findViewById<View>(R.id.habits_button).setOnClickListener {
            startActivity(Intent(this, HabitsActivity::class.java))
        }
        findViewById<View>(R.id.previous_button).setOnClickListener { moveSelection(-1) }
        nextButton.setOnClickListener { moveSelection(1) }
        selectedLabel.setOnClickListener { pickSelectedDate() }
        findViewById<View>(R.id.calendar_button).setOnClickListener { pickSelectedDate() }
        findViewById<View>(R.id.add_entry_button).setOnClickListener { addEntry() }
        rangeAll.setOnClickListener { changeRange(DateRange.ALL) }
        rangeFrom.setOnClickListener { pickRangeStart() }
        rangeTo.setOnClickListener { pickRangeEnd() }
        chart.axisLabel = { formats.axisLabel(period, it) }
        chart.onSelect = { index ->
            buckets.getOrNull(index)?.let { bucket ->
                val now = System.currentTimeMillis()
                selected = if (now in bucket.start until bucket.end) now else bucket.start
                renderSelection()
            }
        }

        requestedHabitId = savedInstanceState?.getLong(STATE_HABIT)
            ?: intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, repository.settings.lastViewedHabitId)
        selected = savedInstanceState?.getLong(STATE_SELECTED) ?: System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedHabitId = intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, requestedHabitId)
        selected = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_HABIT, requestedHabitId)
        outState.putLong(STATE_SELECTED, selected)
    }

    private class Loaded(val habit: Habit, val habits: List<Habit>, val timestamps: LongArray)

    /** Reloads the habit and its timestamps; entries may have been logged from the home screen. */
    private fun load() {
        val id = requestedHabitId
        Async.load({
            repository.ensureFirstRun()
            val all = repository.habits()
            // The requested habit, else the app icon's, else any; null when there are none.
            val h = repository.habit(id) ?: repository.defaultHabit() ?: all.firstOrNull()
            h?.let { Loaded(it, all, repository.timestamps(it.id)) }
        }) { loaded ->
            if (isFinishing) return@load
            if (loaded == null) {
                startActivity(Intent(this, HabitsActivity::class.java))
                finish()
                return@load
            }
            val switched = habit?.id != loaded.habit.id
            habit = loaded.habit
            habits = loaded.habits
            timestamps = loaded.timestamps
            requestedHabitId = loaded.habit.id
            repository.settings.lastViewedHabitId = loaded.habit.id
            if (switched) {
                period = repository.settings.period(loaded.habit.id)
                range = repository.settings.range(loaded.habit.id)
            }
            render()
        }
    }

    private fun render() {
        val h = habit ?: return
        habitIcon.setImageDrawable(IconFactory.drawable(this, h))
        habitName.text = h.name
        periodButtons.forEach { (p, button) -> button.isSelected = p == period }
        renderSummary()
        renderSelection()
    }

    /** Statistics, chart and window chips for the current period and range. */
    private fun renderSummary() {
        val h = habit ?: return
        val now = System.currentTimeMillis()
        val bounds = StatsEngine.resolve(range, timestamps.firstOrNull(), now, math)
        buckets = if (timestamps.isEmpty() && range.isAll) {
            emptyList()
        } else {
            StatsEngine.bucketize(timestamps, period, bounds.first, bounds.last, math)
        }
        val summary = StatsEngine.summarize(buckets, now)

        statsTitle.setText(
            when (period) {
                Period.DAY -> R.string.stats_title_day
                Period.WEEK -> R.string.stats_title_week
                Period.MONTH -> R.string.stats_title_month
                Period.YEAR -> R.string.stats_title_year
            },
        )
        val empty = getString(R.string.stat_empty)
        setStat(R.id.stat_average, R.string.stat_average, summary?.let { formats.number(it.average) } ?: empty)
        setStat(R.id.stat_min, R.string.stat_min, summary?.let { formats.number(it.min) } ?: empty)
        setStat(R.id.stat_max, R.string.stat_max, summary?.let { formats.number(it.max) } ?: empty)
        setStat(R.id.stat_std_dev, R.string.stat_std_dev, summary?.let { formats.number(it.stdDev) } ?: empty)
        setStat(R.id.stat_variance, R.string.stat_variance, summary?.let { formats.number(it.variance) } ?: empty)
        setStat(R.id.stat_total, R.string.stat_total, summary?.let { formats.number(it.total) } ?: empty)

        statsCaption.visible = summary != null
        if (summary != null) {
            val basedOn = resources.getQuantityString(
                when (period) {
                    Period.DAY -> R.plurals.based_on_days
                    Period.WEEK -> R.plurals.based_on_weeks
                    Period.MONTH -> R.plurals.based_on_months
                    Period.YEAR -> R.plurals.based_on_years
                },
                summary.periods,
                summary.periods,
            )
            statsCaption.text = if (summary.excludesCurrent) {
                val excluded = getString(
                    when (period) {
                        Period.DAY -> R.string.excludes_day
                        Period.WEEK -> R.string.excludes_week
                        Period.MONTH -> R.string.excludes_month
                        Period.YEAR -> R.string.excludes_year
                    },
                )
                getString(R.string.caption_join, basedOn, excluded)
            } else {
                basedOn
            }
        }

        rangeAll.isSelected = range.isAll
        val from = range.fromDay?.let { formats.shortDate(it) } ?: getString(R.string.range_start)
        val to = range.toDay?.let { formats.shortDate(it) } ?: getString(R.string.range_today)
        rangeFrom.text = from
        rangeTo.text = to
        rangeFrom.isSelected = range.fromDay != null
        rangeTo.isSelected = range.toDay != null
        rangeFrom.contentDescription = getString(R.string.range_from_description, from)
        rangeTo.contentDescription = getString(R.string.range_to_description, to)

        chart.setData(buckets, selectedBucketIndex(), h.color)
    }

    /** The count card and the entry list for [selected]. */
    private fun renderSelection() {
        val h = habit ?: return
        val now = System.currentTimeMillis()
        val start = math.startOf(period, selected)
        val end = math.endOf(period, selected)
        val count = StatsEngine.countBetween(timestamps, start, end)
        selectedLabel.text = formats.periodTitle(period, selected, now)
        selectedCount.text = formats.number(count)
        selectedCaption.text = resources.getQuantityString(R.plurals.count_entries, count)
        val canGoForward = start < math.startOf(period, now)
        nextButton.isEnabled = canGoForward
        nextButton.alpha = if (canGoForward) 1f else 0.3f
        entriesTitle.text = getString(R.string.caption_join, getString(R.string.entries), selectedLabel.text)
        chart.setSelected(selectedBucketIndex())

        val habitId = h.id
        Async.load({ repository.entries(habitId, start, end, ENTRY_LIMIT) }) { entries ->
            if (habit?.id == habitId && math.startOf(period, selected) == start) showEntries(entries, count)
        }
    }

    private fun showEntries(entries: List<Entry>, total: Int) {
        entriesList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val color = habit?.color ?: 0
        for (entry in entries) {
            val row = inflater.inflate(R.layout.item_entry, entriesList, false)
            val label = if (period == Period.DAY) formats.time(entry.timestamp) else formats.dateTime(entry.timestamp)
            row.findViewById<TextView>(R.id.entry_time).text = label
            row.findViewById<View>(R.id.entry_dot).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            row.contentDescription = getString(R.string.delete_entry_message, label)
            row.setOnClickListener { confirmDelete(entry, label) }
            entriesList.addView(row)
        }
        entriesFooter.visible = entries.isEmpty() || total > entries.size
        entriesFooter.text = if (entries.isEmpty()) {
            getString(R.string.entries_empty)
        } else {
            resources.getQuantityString(R.plurals.entries_more, total - entries.size, total - entries.size)
        }
    }

    private fun setStat(containerId: Int, labelRes: Int, value: String) {
        val container = findViewById<View>(containerId)
        container.findViewById<TextView>(R.id.stat_value).text = value
        container.findViewById<TextView>(R.id.stat_label).setText(labelRes)
    }

    private fun selectedBucketIndex(): Int = buckets.indexOfFirst { selected >= it.start && selected < it.end }

    private fun changePeriod(newPeriod: Period) {
        val h = habit ?: return
        if (newPeriod == period) return
        period = newPeriod
        repository.settings.setPeriod(h.id, newPeriod)
        render()
    }

    private fun changeRange(newRange: DateRange) {
        val h = habit ?: return
        range = newRange
        repository.settings.setRange(h.id, newRange)
        renderSummary()
        renderSelection()
    }

    private fun moveSelection(direction: Int) {
        val now = System.currentTimeMillis()
        val target = math.shift(period, selected, direction)
        selected = if (math.samePeriod(period, target, now)) now else minOf(target, now)
        renderSelection()
    }

    private fun pickSelectedDate() {
        showDatePicker(selected, maxDate = System.currentTimeMillis()) { day ->
            val now = System.currentTimeMillis()
            selected = if (math.samePeriod(Period.DAY, day, now)) now else day
            renderSelection()
        }
    }

    private fun pickRangeStart() {
        val now = System.currentTimeMillis()
        val initial = range.fromDay ?: timestamps.firstOrNull() ?: now
        showDatePicker(initial, maxDate = range.toDay ?: now) { day -> changeRange(range.copy(fromDay = day)) }
    }

    private fun pickRangeEnd() {
        val now = System.currentTimeMillis()
        val minDate = range.fromDay ?: timestamps.firstOrNull()
        showDatePicker(range.toDay ?: now, minDate = minDate, maxDate = now) { day ->
            // Picking today keeps the window open-ended, so it keeps following "today" tomorrow.
            changeRange(range.copy(toDay = if (math.samePeriod(Period.DAY, day, now)) null else day))
        }
    }

    private fun addEntry() {
        if (period == Period.DAY) {
            pickTime(math.startOf(Period.DAY, selected))
        } else {
            showDatePicker(selected, maxDate = System.currentTimeMillis()) { day -> pickTime(day) }
        }
    }

    private fun pickTime(day: Long) {
        val now = Calendar.getInstance()
        val isToday = math.samePeriod(Period.DAY, day, now.timeInMillis)
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val timestamp = Calendar.getInstance().apply {
                    timeInMillis = day
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (timestamp > System.currentTimeMillis()) toast(R.string.future_entry) else insertEntry(timestamp)
            },
            if (isToday) now.get(Calendar.HOUR_OF_DAY) else 12,
            if (isToday) now.get(Calendar.MINUTE) else 0,
            DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun insertEntry(timestamp: Long) {
        val h = habit ?: return
        selected = timestamp
        Async.load({
            repository.log(h.id, timestamp)
            LauncherSync.afterLog(this)
        }) { load() }
    }

    private fun confirmDelete(entry: Entry, label: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(getString(R.string.delete_entry_message, label))
            .setPositiveButton(R.string.delete) { _, _ ->
                Async.load({
                    repository.deleteEntry(entry.id)
                    LauncherSync.afterLog(this)
                }) { load() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showHabitMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        habits.forEachIndexed { index, h -> menu.menu.add(0, index, index, h.name) }
        menu.menu.add(0, MENU_MANAGE, habits.size, R.string.manage_habits)
        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_MANAGE) {
                startActivity(Intent(this, HabitsActivity::class.java))
            } else {
                habits.getOrNull(item.itemId)?.let {
                    requestedHabitId = it.id
                    selected = System.currentTimeMillis()
                    load()
                }
            }
            true
        }
        menu.show()
    }

    private fun showDatePicker(initial: Long, minDate: Long? = null, maxDate: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = minOf(initial, maxDate) }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    clear()
                    set(year, month, dayOfMonth)
                }.timeInMillis
                onPicked(picked)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        )
        if (minDate != null && minDate <= maxDate) dialog.datePicker.minDate = math.startOf(Period.DAY, minDate)
        dialog.datePicker.maxDate = maxDate
        dialog.show()
    }

    companion object {
        private const val STATE_HABIT = "habit"
        private const val STATE_SELECTED = "selected"
        private const val ENTRY_LIMIT = 100
        private const val MENU_MANAGE = 10_000
    }
}
