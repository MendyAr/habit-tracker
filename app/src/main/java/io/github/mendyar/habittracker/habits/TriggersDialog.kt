package io.github.mendyar.habittracker.habits

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.Triggers
import io.github.mendyar.habittracker.ui.colorOf
import io.github.mendyar.habittracker.ui.dp

/**
 * Edits the triggers a habit suggests after logging: remove one, move one up, or
 * type a new one. Names wrap instead of being cut. *Reset to Presets* restores the
 * built-in list. Nothing is saved until *Done*.
 */
object TriggersDialog {

    fun show(activity: Activity, current: List<String>, onChosen: (List<String>) -> Unit): AlertDialog {
        val items = current.toMutableList()
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        fun render() {
            list.removeAllViews()
            items.forEachIndexed { index, name ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = activity.dp(48)
                }
                row.addView(
                    TextView(activity).apply {
                        text = name
                        textSize = 16f
                        setTextColor(activity.colorOf(R.color.text_primary))
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                row.addView(
                    iconButton(activity, R.drawable.ic_ui_expand_more, activity.getString(R.string.trigger_move_up, name), rotation = 180f) {
                        if (index > 0) {
                            items.add(index - 1, items.removeAt(index))
                            render()
                        }
                    }.apply { isEnabled = index > 0; alpha = if (index > 0) 1f else 0.3f },
                )
                row.addView(
                    iconButton(activity, R.drawable.ic_ui_close, activity.getString(R.string.trigger_remove, name)) {
                        items.removeAt(index)
                        render()
                    },
                )
                list.addView(row)
            }
        }
        render()

        val field = EditText(activity).apply {
            setHint(R.string.trigger_new_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(Triggers.MAX_LENGTH))
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        fun addTyped() {
            val name = Triggers.clean(field.text.toString()) ?: return
            items.clear()
            items.addAll(Triggers.added(items.toList(), name))
            field.setText("")
            render()
        }
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) addTyped()
            true
        }
        val addRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton(activity, R.drawable.ic_ui_add, activity.getString(R.string.add)) { addTyped() })
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(4), activity.dp(12), 0)
            addView(
                TextView(activity).apply {
                    setText(R.string.edit_triggers_hint)
                    textSize = 14f
                    setTextColor(activity.colorOf(R.color.text_secondary))
                    setPadding(0, 0, activity.dp(12), activity.dp(8))
                },
            )
            addView(list)
            addView(addRow)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.edit_triggers_title)
            .setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton(R.string.done) { _, _ ->
                // A name typed but not added yet is kept too.
                Triggers.clean(field.text.toString())?.let { name ->
                    val withTyped = Triggers.added(items, name)
                    items.clear()
                    items.addAll(withTyped)
                }
                onChosen(items.toList())
            }
            .setNeutralButton(R.string.triggers_reset) { _, _ -> onChosen(Triggers.PRESETS) }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return dialog
    }

    private fun iconButton(activity: Activity, icon: Int, description: String, rotation: Float = 0f, onClick: () -> Unit) =
        ImageButton(activity).apply {
            setImageResource(icon)
            this.rotation = rotation
            contentDescription = description
            val value = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
            setBackgroundResource(value.resourceId)
            minimumWidth = activity.dp(48)
            minimumHeight = activity.dp(48)
            setOnClickListener { onClick() }
        }
}
