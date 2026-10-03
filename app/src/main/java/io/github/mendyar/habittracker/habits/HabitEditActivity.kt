package io.github.mendyar.habittracker.habits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.data.Habit
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.icons.HabitIcons
import io.github.mendyar.habittracker.icons.IconFactory
import io.github.mendyar.habittracker.launcher.LauncherSync
import io.github.mendyar.habittracker.launcher.Shortcuts
import io.github.mendyar.habittracker.ui.Async
import io.github.mendyar.habittracker.ui.colorOf
import io.github.mendyar.habittracker.ui.dp
import io.github.mendyar.habittracker.ui.toast
import io.github.mendyar.habittracker.ui.visible
import io.github.mendyar.habittracker.widget.HabitWidgetProvider
import java.io.File

/**
 * Creates or edits a habit: name, colour, a built-in or user-supplied icon, its
 * home-screen icon / widget, whether the main app icon logs it, and deletion.
 */
class HabitEditActivity : Activity() {

    private lateinit var repository: HabitRepository

    private var habitId = NEW
    private var original: Habit? = null
    private var iconKey = HabitIcons.DEFAULT
    private var customIcon: String? = null
    private var color = HabitColors.DEFAULT
    private var isDefault = false
    private var entryCount = 0

    private lateinit var title: TextView
    private lateinit var preview: ImageView
    private lateinit var nameField: EditText
    private lateinit var colorRow: LinearLayout
    private lateinit var iconGrid: LinearLayout
    private lateinit var mainIconSwitch: Switch
    private val iconCells = mutableMapOf<String, View>()
    private lateinit var uploadCell: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_habit_edit)
        repository = HabitRepository.get(this)
        habitId = intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, NEW)

        title = findViewById(R.id.edit_title)
        preview = findViewById(R.id.edit_preview)
        nameField = findViewById(R.id.edit_name)
        colorRow = findViewById(R.id.edit_colors)
        iconGrid = findViewById(R.id.edit_icons)
        mainIconSwitch = findViewById(R.id.edit_main_icon)

        findViewById<View>(R.id.back_button).setOnClickListener { discardAndFinish() }
        findViewById<View>(R.id.save_button).setOnClickListener { save { finishSaved(it) } }
        nameField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) nameField.clearFocus()
            false
        }
        findViewById<View>(R.id.edit_add_home).setOnClickListener { save(::pinIcon) }
        findViewById<View>(R.id.edit_add_widget).apply {
            visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            setOnClickListener { save(::pinWidget) }
        }
        findViewById<View>(R.id.edit_delete).setOnClickListener { confirmDelete() }
        findViewById<View>(R.id.edit_clear).setOnClickListener { confirmClearEntries() }

        buildColorRow()
        buildIconGrid()

        if (savedInstanceState != null) {
            iconKey = savedInstanceState.getString(STATE_ICON, HabitIcons.DEFAULT)
            customIcon = savedInstanceState.getString(STATE_CUSTOM)
            color = savedInstanceState.getInt(STATE_COLOR, HabitColors.DEFAULT)
        }

        if (habitId == NEW) {
            title.setText(R.string.new_habit)
            // Pinning, clearing and deleting need a saved habit; the app-icon switch does not.
            listOf(R.id.edit_add_home, R.id.edit_add_widget, R.id.edit_danger_card).forEach { findViewById<View>(it).visible = false }
            if (savedInstanceState == null) {
                Async.load({ repository.habits().size }) { count ->
                    color = HabitColors.ALL[count % HabitColors.ALL.size]
                    refresh()
                }
            }
            refresh()
        } else {
            title.setText(R.string.edit_habit)
            Async.load({
                val habit = repository.habit(habitId)
                Triple(habit, repository.settings.defaultHabitId == habitId, repository.timestamps(habitId).size)
            }) { (habit, default, count) ->
                if (habit == null) {
                    finish()
                    return@load
                }
                original = habit
                isDefault = default
                entryCount = count
                if (savedInstanceState == null) {
                    nameField.setText(habit.name)
                    nameField.setSelection(habit.name.length)
                    iconKey = habit.icon
                    customIcon = habit.customIcon
                    color = habit.color
                }
                if (savedInstanceState == null) mainIconSwitch.isChecked = default
                refresh()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ICON, iconKey)
        outState.putString(STATE_CUSTOM, customIcon)
        outState.putInt(STATE_COLOR, color)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        discardAndFinish()
    }

    private fun buildColorRow() {
        val size = dp(40)
        HabitColors.ALL.forEachIndexed { index, value ->
            val dot = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(10) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(value)
                }
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = getString(R.string.color_description, index + 1)
                tag = value
                setOnClickListener {
                    color = value
                    refresh()
                }
            }
            colorRow.addView(dot)
        }
    }

    private fun buildIconGrid() {
        val widthDp = resources.configuration.screenWidthDp - 2 * 16 - 16
        val columns = (widthDp / 60).coerceIn(5, 10)
        val cells = mutableListOf<View>()

        uploadCell = iconCell().apply {
            setImageResource(R.drawable.ic_ui_add_photo_alternate)
            contentDescription = getString(R.string.upload_icon)
            setOnClickListener { pickImage() }
        }
        cells += uploadCell
        for (icon in HabitIcons.ALL) {
            cells += iconCell().apply {
                setImageDrawable(getDrawable(icon.drawable)?.mutate()?.apply { setTint(colorOf(R.color.text_primary)) })
                contentDescription = getString(icon.label)
                setOnClickListener {
                    iconKey = icon.key
                    dropCustomIcon()
                    refresh()
                }
                iconCells[icon.key] = this
            }
        }

        cells.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { row.addView(it) }
            repeat(columns - chunk.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
            }
            iconGrid.addView(row)
        }
    }

    private fun iconCell() = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        background = getDrawable(R.drawable.bg_icon_cell)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
    }

    /** Re-renders preview and selection states from the current editor state. */
    private fun refresh() {
        preview.setImageDrawable(IconFactory.drawable(this, iconKey, customIcon, color))
        for (i in 0 until colorRow.childCount) {
            val dot = colorRow.getChildAt(i) as ImageView
            val selected = dot.tag == color
            dot.setImageDrawable(
                if (selected) getDrawable(R.drawable.ic_ui_check)?.mutate()?.apply { setTint(0xFFFFFFFF.toInt()) } else null,
            )
        }
        iconCells.forEach { (key, cell) -> cell.isSelected = customIcon == null && key == iconKey }
        uploadCell.isSelected = customIcon != null
        if (customIcon != null) {
            uploadCell.setPadding(dp(6), dp(6), dp(6), dp(6))
            uploadCell.setImageDrawable(IconFactory.drawable(this, iconKey, customIcon, color))
        } else {
            uploadCell.setPadding(dp(12), dp(12), dp(12), dp(12))
            uploadCell.setImageResource(R.drawable.ic_ui_add_photo_alternate)
        }
    }

    private fun pickImage() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
        } else {
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, REQUEST_IMAGE)
        } catch (e: android.content.ActivityNotFoundException) {
            toast(R.string.image_failed)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || uri == null) return
        Async.load({ IconFactory.importImage(this, uri) }) { path ->
            if (path == null) {
                toast(R.string.image_failed)
            } else {
                dropCustomIcon()
                customIcon = path
                refresh()
            }
        }
    }

    /** Forgets the current custom image, deleting it if it was never saved. */
    private fun dropCustomIcon() {
        val current = customIcon ?: return
        if (current != original?.customIcon) File(current).delete()
        customIcon = null
    }

    /** Validates and stores the habit, then hands the saved version to [then]. */
    private fun save(then: (Habit) -> Unit) {
        val name = nameField.text.toString().trim()
        if (name.isEmpty()) {
            nameField.error = getString(R.string.name_required)
            nameField.requestFocus()
            return
        }
        val wantsDefault = mainIconSwitch.isChecked
        val wasDefault = isDefault
        val icon = iconKey
        val custom = customIcon
        val chosenColor = color
        val existing = original
        Async.load({
            val saved = if (existing == null) {
                repository.createHabit(name, icon, custom, chosenColor)
            } else {
                existing.copy(name = name, icon = icon, customIcon = custom, color = chosenColor)
                    .also { repository.updateHabit(it) }
            }
            // Switching it off leaves the app icon without a habit: a tap then opens the list.
            if (wantsDefault) repository.setDefaultHabit(saved.id) else if (wasDefault) repository.setDefaultHabit(null)
            LauncherSync.refresh(this)
            saved
        }) { saved ->
            original = saved
            habitId = saved.id
            isDefault = wantsDefault
            then(saved)
        }
    }

    private fun finishSaved(saved: Habit) {
        val created = intent.getLongExtra(Shortcuts.EXTRA_HABIT_ID, NEW) == NEW
        setResult(RESULT_OK, Intent().putExtra(Shortcuts.EXTRA_HABIT_ID, saved.id))
        if (created && !intent.getBooleanExtra(EXTRA_FOR_WIDGET, false)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.add_to_home_title)
                .setMessage(getString(R.string.add_to_home_message, saved.name))
                .setPositiveButton(R.string.add) { _, _ ->
                    pinIcon(saved)
                    finish()
                }
                .setNegativeButton(R.string.not_now) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        } else {
            finish()
        }
    }

    private fun pinIcon(habit: Habit) {
        if (!Shortcuts.requestPin(this, habit)) {
            toast(R.string.pin_unsupported)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(R.string.pin_legacy_done)
        }
    }

    private fun pinWidget(habit: Habit) {
        val app = applicationContext
        Async.load({ HabitWidgetProvider.requestPin(app, habit) }) { result ->
            when (result) {
                HabitWidgetProvider.PinResult.ALREADY_EXISTS -> toast(R.string.widget_exists)
                HabitWidgetProvider.PinResult.UNSUPPORTED -> toast(R.string.pin_unsupported)
                HabitWidgetProvider.PinResult.REQUESTED -> Unit
            }
        }
    }

    private fun confirmClearEntries() {
        val habit = original ?: return
        if (entryCount == 0) {
            toast(R.string.clear_entries_nothing)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_entries_title, habit.name))
            .setMessage(resources.getQuantityString(R.plurals.clear_entries_message, entryCount, entryCount))
            .setPositiveButton(R.string.clear) { _, _ ->
                Async.load({
                    repository.clearEntries(habit.id)
                    LauncherSync.afterLog(this)
                }) {
                    entryCount = 0
                    toast(R.string.clear_entries_done)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        val habit = original ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_habit_title, habit.name))
            .setMessage(
                resources.getQuantityString(R.plurals.delete_habit_message, entryCount, entryCount) +
                    "\n\n" + getString(R.string.delete_habit_home_note),
            )
            .setPositiveButton(R.string.delete) { _, _ ->
                dropCustomIcon()
                Async.load({
                    repository.deleteHabit(habit.id)
                    Shortcuts.disable(this, habit.id)
                    LauncherSync.refresh(this)
                }) { finish() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun discardAndFinish() {
        dropCustomIcon()
        finish()
    }

    companion object {
        private const val NEW = -1L
        private const val REQUEST_IMAGE = 1
        private const val STATE_ICON = "icon"
        private const val STATE_CUSTOM = "custom_icon"
        private const val STATE_COLOR = "color"
        const val EXTRA_FOR_WIDGET = "for_widget"

        /** Opens the editor for [habitId], or for a new habit when null. */
        fun intent(context: Context, habitId: Long?): Intent =
            Intent(context, HabitEditActivity::class.java).putExtra(Shortcuts.EXTRA_HABIT_ID, habitId ?: NEW)
    }
}
