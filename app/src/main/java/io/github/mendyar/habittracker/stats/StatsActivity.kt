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
import io.github.mendyar.habittracker.core.DayProfile
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.core.PeriodMath
import io.github.mendyar.habittracker.core.PositionProfile
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.core.RobustStats
import io.github.mendyar.habittracker.data.Entry
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.habits.HabitEditActivity
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.DuplicateDialog
import io.github.mendyar.habittracker.ui.Formats
import io.github.mendyar.habittracker.widget.HabitWidgetProvider
import io.github.mendyar.habittracker.ui.dp
import io.github.mendyar.habittracker.ui.toast
import io.github.mendyar.habittracker.ui.visible
import java.util.Calendar

/**
 * Statistics of one habit: the count of a chosen day/week/month/year, average,
 * min, max, standard deviation and variance per period, a bar chart over a
 * user-defined window, how likely the habit is at each time of day over that
 * window, and the individual entries (which can be added or removed).
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
    private var profile: DayProfile? = null
    private var config = StatsConfig()
    private var sessions: List<Entry> = emptyList()
    private var positions: PositionProfile? = null

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
    private lateinit var timeTitle: TextView
    private lateinit var statsGrid: LinearLayout
    private lateinit var durationsCard: View
    private lateinit var durationsChart: DurationsChartView
    private lateinit var historyCard: View
    private lateinit var timeCard: View
    private lateinit var entriesCard: View
    private lateinit var timeReadout: TextView
    private lateinit var timeChart: DayProfileView
    private lateinit var timeCaption: TextView
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
        timeTitle = findViewById(R.id.time_title)
        statsGrid = findViewById(R.id.stats_grid)
        durationsCard = findViewById(R.id.durations_card)
        durationsChart = findViewById(R.id.durations_chart)
        durationsChart.lengthLabel = formats::elapsed
        findViewById<View>(R.id.durations_title).setOnClickListener { explain(R.string.durations, R.string.help_durations) }
        historyCard = findViewById(R.id.history_card)
        timeCard = findViewById(R.id.time_card)
        entriesCard = findViewById(R.id.entries_card)
        timeReadout = findViewById(R.id.time_readout)
        timeChart = findViewById(R.id.time_chart)
        timeCaption = findViewById(R.id.time_caption)
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
        // Tapping a title or value explains it in a line or two.
        findViewById<View>(R.id.stats_customize).setOnClickListener { customize() }
        statsTitle.setOnClickListener { explain(R.string.statistics_settings, R.string.help_stats_card) }
        findViewById<View>(R.id.history_title).setOnClickListener { explain(R.string.history, R.string.help_history) }
        timeTitle.setOnClickListener {
            explain(R.string.time_of_day, if (period == Period.DAY) R.string.help_time_of_day else R.string.help_positions)
        }
        entriesTitle.setOnClickListener { explain(R.string.entries, R.string.help_entries) }
        selectedCount.setOnClickListener { explain(R.string.count_card_title, R.string.help_count_card) }
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
        timeChart.hourLabel = formats::hourLabel
        timeChart.percentLabel = formats::percent
        timeChart.onSelect = ::renderTimeReadout
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

    private class Loaded(val habit: Habit, val habits: List<Habit>, val timestamps: LongArray, val sessions: List<Entry>)

    /** Reloads the habit and its timestamps; entries may have been logged from the home screen. */
    private fun load() {
        val id = requestedHabitId
        Async.load({
            val all = repository.habits()
            // The requested habit, else any; null when there are none.
            val h = repository.habit(id) ?: all.firstOrNull()
            h?.let { Loaded(it, all, repository.timestamps(it.id), repository.sessions(it.id)) }
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
            sessions = loaded.sessions
            requestedHabitId = loaded.habit.id
            repository.settings.lastViewedHabitId = loaded.habit.id
            config = StatsConfig.decode(repository.settings.statsConfig(loaded.habit.id))
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
        // Duration habits: the finished sessions that started in the window.
        val durations = sessions.filter { it.timestamp in bounds }.map { it.duration ?: 0L }.toLongArray()
        renderStats(StatValues(this, formats, timestamps, buckets, bounds, now, math, config, durations))
        durationsChart.setData(durations, h.color)

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
        val finished = buckets.count { it.end <= now }.coerceAtLeast(if (buckets.isEmpty()) 0 else 1)
        val counts = DoubleArray(buckets.size) { buckets[it].count.toDouble() }
        val flags = RobustStats.highOutliers(counts.copyOf(finished))
        chart.setTrend(RobustStats.rollingMedian(counts), BooleanArray(buckets.size) { it < finished && flags[it] })
        applyCards()
        renderTimeOfDay(bounds, now, h.color)
    }

    /**
     * When the habit usually happens, over the same window as the history chart:
     * by time of day on the Day tab, by weekday, day of the month or month on the
     * others.
     */
    private fun renderTimeOfDay(bounds: LongRange, now: Long, color: Int) {
        timeTitle.setText(
            when (period) {
                Period.DAY -> R.string.time_of_day
                Period.WEEK -> R.string.day_of_week
                Period.MONTH -> R.string.day_of_month
                Period.YEAR -> R.string.month_of_year
            },
        )
        if (period != Period.DAY) {
            renderPositions(bounds, now, color)
            return
        }
        positions = null
        val p = DayProfile.of(timestamps, bounds.first, bounds.last, now, math)
        profile = p
        val empty = p.peak <= 0.0
        timeChart.setData(p, if (empty) -1 else p.peakIndex, color, config.dayStartHour ?: 0)
        timeReadout.visible = !empty
        timeCaption.visible = !empty
        val peakTime = formats.timeOfDay(p.minuteAt(p.peakIndex))
        timeChart.contentDescription = if (empty) {
            getString(R.string.chart_empty)
        } else {
            getString(R.string.time_chart_description, peakTime, formats.percent(p.peak))
        }
        if (!empty) timeReadout.text = getString(R.string.time_peak, peakTime, formats.percent(p.peak))
        val basedOn = resources.getQuantityString(R.plurals.based_on_days, p.days, p.days)
        val days = if (p.excludesToday) getString(R.string.caption_join, basedOn, getString(R.string.excludes_day)) else basedOn
        timeCaption.text = getString(R.string.time_explained) + "\n" + days
    }

    /** Weekday, day-of-month or month bars for the Week, Month and Year tabs. */
    private fun renderPositions(bounds: LongRange, now: Long, color: Int) {
        val p = PositionProfile.of(timestamps, period, bounds.first, bounds.last, now, math)
        positions = p
        profile = null
        val empty = p.peak <= 0.0
        val labels = p.positions.mapIndexed { i, position ->
            when (period) {
                Period.WEEK -> formats.weekday(position, short = true)
                Period.MONTH -> if (i % 7 == 0) position.toString() else ""
                else -> formats.month(position, short = true)
            }
        }
        timeChart.setBars(p.values, labels, if (empty) -1 else p.peakIndex, color)
        timeReadout.visible = !empty
        timeCaption.visible = !empty
        val peakName = positionName(p, p.peakIndex)
        timeChart.contentDescription = if (empty) {
            getString(R.string.chart_empty)
        } else {
            getString(R.string.time_chart_description_position, timeTitle.text, peakName, formats.percent(p.peak))
        }
        if (!empty) timeReadout.text = getString(R.string.time_peak_position, peakName, formats.percent(p.peak))

        val months = period == Period.YEAR
        val basedOn = resources.getQuantityString(if (months) R.plurals.based_on_months else R.plurals.based_on_days, p.units, p.units)
        val counted = if (p.excludesCurrent) {
            getString(R.string.caption_join, basedOn, getString(if (months) R.string.excludes_month else R.string.excludes_day))
        } else {
            basedOn
        }
        val explained = getString(
            when (period) {
                Period.WEEK -> R.string.time_explained_week
                Period.MONTH -> R.string.time_explained_month
                else -> R.string.time_explained_year
            },
        )
        val rough = if (p.minSamples < ROUGH_BELOW) "\n" + getString(R.string.time_little_data) else ""
        timeCaption.text = explained + "\n" + counted + rough
    }

    private fun positionName(p: PositionProfile, index: Int): String {
        val position = p.positions[index]
        return when (p.period) {
            Period.WEEK -> formats.weekday(position, short = false)
            Period.MONTH -> getString(R.string.time_day_of_month, position)
            else -> formats.month(position, short = false)
        }
    }

    /** Shows the share at the point of the chart the user touched. */
    private fun renderTimeReadout(index: Int) {
        positions?.let { p ->
            val share = formats.percent(p.values[index])
            val name = positionName(p, index)
            timeReadout.text = getString(if (index == p.peakIndex) R.string.time_peak_position else R.string.time_at_position, name, share)
            return
        }
        val p = profile ?: return
        val share = formats.percent(p.values[index])
        timeReadout.text = if (index == p.peakIndex) {
            getString(R.string.time_peak, formats.timeOfDay(p.minuteAt(index)), share)
        } else {
            getString(R.string.time_at, formats.timeOfDay(p.minuteAt(index)), share)
        }
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
        selectedCaption.text = resources.getQuantityString(
            if (h.type == HabitType.DURATION) R.plurals.count_sessions else R.plurals.count_entries,
            count,
        )
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
            val time = if (period == Period.DAY) formats.time(entry.timestamp) else formats.dateTime(entry.timestamp)
            val label = when {
                habit?.type != HabitType.DURATION -> time
                entry.duration == null -> getString(R.string.entry_running, time)
                else -> getString(R.string.entry_with_duration, time, formats.elapsed(entry.duration))
            }
            row.findViewById<TextView>(R.id.entry_time).text = label
            row.findViewById<View>(R.id.entry_dot).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            row.contentDescription = getString(R.string.delete_entry_message, label)
            row.setOnClickListener {
                if (habit?.type == HabitType.DURATION && entry.duration == null) finishOrDiscard(entry, label) else confirmDelete(entry, label)
            }
            entriesList.addView(row)
        }
        entriesFooter.visible = entries.isEmpty() || total > entries.size
        entriesFooter.text = if (entries.isEmpty()) {
            getString(R.string.entries_empty)
        } else {
            resources.getQuantityString(R.plurals.entries_more, total - entries.size, total - entries.size)
        }
    }

    /** The tracker's chosen statistics, three per row; tapping one explains it. */
    private fun renderStats(values: StatValues) {
        statsGrid.removeAllViews()
        val inflater = LayoutInflater.from(this)
        config.statsFor(habit?.type ?: HabitType.COUNT).chunked(STATS_PER_ROW).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            chunk.forEach { kind ->
                val cell = inflater.inflate(R.layout.item_stat, row, false)
                val value = values.text(kind)
                cell.findViewById<TextView>(R.id.stat_value).text = value
                cell.findViewById<TextView>(R.id.stat_label).setText(kind.label)
                cell.contentDescription = getString(R.string.caption_join, getString(kind.label), value)
                cell.setOnClickListener { explain(kind.label, kind.help) }
                row.addView(cell)
            }
            repeat(STATS_PER_ROW - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f)) }
            statsGrid.addView(row)
        }
    }

    /** A one- or two-line explanation of a statistic or chart. */
    private fun explain(title: Int, text: Int) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Lets the user choose this tracker's statistics; applies them right away. */
    private fun customize() {
        val h = habit ?: return
        StatsCustomizeDialog.show(this, config, h.type) { chosen ->
            config = chosen
            repository.settings.setStatsConfig(h.id, chosen.takeUnless { it.isDefault }?.encode())
            render()
        }
    }

    private fun applyCards() {
        historyCard.visible = StatsCard.HISTORY !in config.hiddenCards
        timeCard.visible = StatsCard.WHEN !in config.hiddenCards
        entriesCard.visible = StatsCard.ENTRIES !in config.hiddenCards
        durationsCard.visible = habit?.type == HabitType.DURATION && StatsCard.DURATIONS !in config.hiddenCards
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
                when {
                    timestamp > System.currentTimeMillis() -> toast(R.string.future_entry)
                    habit?.type == HabitType.DURATION -> askSessionLength(timestamp)
                    else -> insertEntry(timestamp)
                }
            },
            if (isToday) now.get(Calendar.HOUR_OF_DAY) else 12,
            if (isToday) now.get(Calendar.MINUTE) else 0,
            DateFormat.is24HourFormat(this),
        ).show()
    }

    /** Adds a manual entry, asking first if that minute already has one. */
    private fun insertEntry(timestamp: Long) {
        val h = habit ?: return
        Async.load({ repository.settings.confirmDuplicates(h.id) && repository.hasEntryInMinute(h.id, timestamp) }) { exists ->
            if (exists) DuplicateDialog.sameMinute(this, h, timestamp) { saveEntry(timestamp) } else saveEntry(timestamp)
        }
    }

    private fun saveEntry(timestamp: Long) {
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

    /** A session added by hand: its start was picked, now its length in minutes. */
    private fun askSessionLength(start: Long) {
        val h = habit ?: return
        val minutes = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setHint(R.string.add_session_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_session_title)
            .setView(minutes)
            .setPositiveButton(R.string.add) { _, _ ->
                val length = minutes.text.toString().toLongOrNull()?.takeIf { it > 0 } ?: return@setPositiveButton
                selected = start
                Async.load({ repository.addSession(h.id, start, length * 60_000L) }) { load() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** A running session in the list: finish it now, or discard it. */
    private fun finishOrDiscard(entry: Entry, label: String) {
        val h = habit ?: return
        AlertDialog.Builder(this)
            .setTitle(label)
            .setPositiveButton(R.string.finish_now) { _, _ ->
                Async.load({
                    repository.finish(entry.id, System.currentTimeMillis())
                    HabitWidgetProvider.updateFor(applicationContext, h.id)
                }) { load() }
            }
            .setNeutralButton(R.string.discard) { _, _ ->
                Async.load({
                    repository.deleteEntry(entry.id)
                    HabitWidgetProvider.updateFor(applicationContext, h.id)
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
        private const val STATS_PER_ROW = 3

        /** Below this many samples per bar, the Week/Month/Year chart says it is rough. */
        private const val ROUGH_BELOW = 4
    }
}
