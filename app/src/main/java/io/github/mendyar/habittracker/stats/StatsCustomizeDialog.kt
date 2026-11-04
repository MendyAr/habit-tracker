package io.github.mendyar.habittracker.stats

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.ui.Formats
import io.github.mendyar.habittracker.ui.colorOf
import io.github.mendyar.habittracker.ui.dp

/**
 * One simple page to choose what a tracker's statistics show: which statistics,
 * which cards, a target and when the day starts. *Reset to Default* is always
 * there. Used from the statistics screen and from the habit editor.
 */
object StatsCustomizeDialog {

    fun show(activity: Activity, current: StatsConfig, onChosen: (StatsConfig) -> Unit): AlertDialog {
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

        section(R.string.customize_stats)
        val statBoxes = StatKind.entries.associateWith { kind ->
            CheckBox(activity).apply {
                setText(kind.label)
                isChecked = kind in current.stats
                content.addView(this)
            }
        }

        section(R.string.customize_cards)
        val cardLabels = mapOf(
            StatsCard.HISTORY to R.string.history,
            StatsCard.WHEN to R.string.time_of_day,
            StatsCard.ENTRIES to R.string.entries,
        )
        val cardBoxes = StatsCard.entries.associateWith { card ->
            CheckBox(activity).apply {
                setText(cardLabels.getValue(card))
                isChecked = card !in current.hiddenCards
                content.addView(this)
            }
        }

        section(R.string.customize_target)
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
                onChosen(
                    StatsConfig(
                        stats = StatKind.entries.filter { statBoxes.getValue(it).isChecked },
                        hiddenCards = StatsCard.entries.filterNot { cardBoxes.getValue(it).isChecked }.toSet(),
                        target = target.text.toString().toIntOrNull()?.takeIf { it >= 0 },
                        dayStartHour = (dayStart.selectedItemPosition - 1).takeIf { it >= 0 },
                    ),
                )
            }
            .setNeutralButton(R.string.customize_reset) { _, _ -> onChosen(StatsConfig()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
