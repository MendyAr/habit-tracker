package io.github.mendyar.habittracker.stats

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.Period
import io.github.mendyar.habittracker.data.HabitType
import io.github.mendyar.habittracker.ui.Formats
import io.github.mendyar.habittracker.ui.colorOf
import io.github.mendyar.habittracker.ui.dp

/**
 * One simple page to choose what a tracker's statistics show: the period they are
 * grouped by (the only place to change it), which statistics, which cards, a
 * target and when the day starts. *Reset to Default* is always there (it keeps the
 * period). Used from the statistics screen and from the habit editor.
 */
object StatsCustomizeDialog {

    fun show(
        activity: Activity,
        current: StatsConfig,
        type: HabitType,
        period: Period,
        onChosen: (StatsConfig, Period) -> Unit,
    ): AlertDialog {
        val formats = Formats(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(8), activity.dp(24), 0)
        }
        fun section(text: Int) = content.addView(
            TextView(activity).apply {
                setText(text)
                setTextColor(activity.colorOf(R.color.text_secondary))
                textSize = 14f
                setPadding(0, activity.dp(16), 0, activity.dp(4))
            },
        )

        section(R.string.customize_period)
        val periodLabels = mapOf(
            Period.DAY to R.string.period_day,
            Period.WEEK to R.string.period_week,
            Period.MONTH to R.string.period_month,
            Period.YEAR to R.string.period_year,
        )
        val periods = RadioGroup(activity).apply { orientation = RadioGroup.HORIZONTAL }
        val periodButtons = Period.entries.associateWith { p ->
            RadioButton(activity).apply {
                id = View.generateViewId()
                setText(periodLabels.getValue(p))
                setPadding(0, 0, activity.dp(12), 0)
                periods.addView(this)
            }
        }
        periodButtons.getValue(period).isChecked = true
        content.addView(periods)
        fun chosenPeriod() = periodButtons.entries.firstOrNull { it.value.isChecked }?.key ?: period

        section(R.string.customize_stats)
        val shown = current.statsFor(type)
        val statBoxes = StatKind.available(type).associateWith { kind ->
            CheckBox(activity).apply {
                text = activity.getString(kind.label, formats.unit(period), formats.units(period))
                isChecked = kind in shown
                content.addView(this)
            }
        }

        section(R.string.customize_cards)
        val shownCards = current.cardsFor(type)
        val cards = StatsCard.available(type)
        val cardBoxes = cards.associateWith { card ->
            CheckBox(activity).apply {
                setText(card.label)
                isChecked = card in shownCards
                content.addView(this)
            }
        }

        section(if (type == HabitType.DURATION) R.string.customize_target_duration else R.string.customize_target)
        val target = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setHint(R.string.customize_target_hint)
            setText(current.target?.toString().orEmpty())
            content.addView(this)
        }

        section(R.string.customize_day_start)
        val hours = listOf(activity.getString(R.string.statistics_settings_default)) + (0..23).map { formats.hourLabel(it) }
        val dayStart = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, hours)
            setSelection(current.dayStartHour?.plus(1) ?: 0)
            content.addView(this)
        }

        return AlertDialog.Builder(activity)
            .setTitle(R.string.customize_title)
            .setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton(R.string.save) { _, _ ->
                val chosenCards = cards.filter { cardBoxes.getValue(it).isChecked }.toSet()
                onChosen(
                    StatsConfig(
                        stats = statBoxes.keys.filter { statBoxes.getValue(it).isChecked }.takeIf { it != StatKind.defaults(type) },
                        cards = chosenCards.takeIf { it != StatsCard.defaults(type).filter { c -> c.availableFor(type) }.toSet() },
                        target = target.text.toString().toIntOrNull()?.takeIf { it >= 0 },
                        dayStartHour = (dayStart.selectedItemPosition - 1).takeIf { it >= 0 },
                    ),
                    chosenPeriod(),
                )
            }
            .setNeutralButton(R.string.customize_reset) { _, _ -> onChosen(StatsConfig(), chosenPeriod()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
